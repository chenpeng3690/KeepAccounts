package com.local.notebook.ui.screens.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.abs

data class AccountEditUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val name: String = "",
    val type: String = "CASH",
    val balanceInput: String = "",
    val creditLimitInput: String = "",
    val billDayInput: String = "",
    val repayDayInput: String = "",
    val includeInNetWorth: Boolean = true,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null
) {
    val isCredit: Boolean
        get() = type == "CREDIT" || type == "BNPL" || type == "LOAN"

    val saveEnabled: Boolean
        get() = name.isNotBlank() && balanceInput.toBigDecimalOrNull() != null && !saving
}

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val repository: LedgerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<Long>("id") ?: -1L

    private val _state = MutableStateFlow(AccountEditUiState())
    val state: StateFlow<AccountEditUiState> = _state

    init {
        if (accountId > 0) {
            viewModelScope.launch {
                val account = repository.getAccount(accountId)
                if (account == null) {
                    _state.update { it.copy(loading = false, error = "账户不存在") }
                    return@launch
                }
                val balance = if (account.type in CREDIT_TYPES) abs(account.initialBalance) else account.initialBalance
                _state.value = AccountEditUiState(
                    loading = false,
                    isEdit = true,
                    name = account.name,
                    type = account.type,
                    balanceInput = plainMoney(balance),
                    creditLimitInput = account.creditLimit?.let(::plainMoney).orEmpty(),
                    billDayInput = account.billDay?.toString().orEmpty(),
                    repayDayInput = account.repayDay?.toString().orEmpty(),
                    includeInNetWorth = account.includeInNetWorth
                )
            }
        } else {
            _state.update { it.copy(loading = false) }
        }
    }

    fun setName(value: String) {
        _state.update { it.copy(name = value, error = null) }
    }

    fun setType(type: String) {
        _state.update { it.copy(type = type) }
    }

    fun setBalance(value: String) {
        _state.update { it.copy(balanceInput = value, error = null) }
    }

    fun setCreditLimit(value: String) {
        _state.update { it.copy(creditLimitInput = value) }
    }

    fun setBillDay(value: String) {
        _state.update { it.copy(billDayInput = value.filter { it.isDigit() }.take(2)) }
    }

    fun setRepayDay(value: String) {
        _state.update { it.copy(repayDayInput = value.filter { it.isDigit() }.take(2)) }
    }

    fun setIncludeInNetWorth(value: Boolean) {
        _state.update { it.copy(includeInNetWorth = value) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val name = current.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "请输入账户名称") }
            return
        }
        val rawBalance = current.balanceInput.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.movePointRight(2)
            ?.toLong()
        if (rawBalance == null) {
            _state.update { it.copy(error = "请输入有效金额") }
            return
        }
        val initial = if (current.isCredit) -abs(rawBalance) else rawBalance
        val limit = current.creditLimitInput.trim().toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.movePointRight(2)
            ?.toLong()
            ?.takeIf { it > 0 }
        val billDay = current.billDayInput.toIntOrNull()?.takeIf { it in 1..31 }
        val repayDay = current.repayDayInput.toIntOrNull()?.takeIf { it in 1..31 }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            if (accountId > 0) {
                repository.updateAccount(
                    id = accountId,
                    name = name,
                    type = current.type,
                    initialBalance = initial,
                    creditLimit = limit,
                    billDay = billDay,
                    repayDay = repayDay,
                    includeInNetWorth = current.includeInNetWorth
                )
            } else {
                repository.addAccount(
                    name = name,
                    type = current.type,
                    initialBalance = initial,
                    creditLimit = limit,
                    billDay = billDay,
                    repayDay = repayDay,
                    includeInNetWorth = current.includeInNetWorth
                )
            }
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    private fun plainMoney(cents: Long): String =
        BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private companion object {
        val CREDIT_TYPES = setOf("CREDIT", "BNPL", "LOAN")
    }
}
