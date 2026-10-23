package com.local.notebook.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.data.local.TxnEditLogEntity
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class TxnEditUiState(
    val loading: Boolean = true,
    val exists: Boolean = true,
    val confirmMode: Boolean = false,
    val amountText: String = "",
    val type: String = "EXPENSE",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val source: String = "MANUAL",
    val editCount: Int = 0,
    val logs: List<TxnEditLogEntity> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TxnEditViewModel @Inject constructor(
    private val repository: LedgerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val txnId: Long = savedStateHandle.get<Long>("id") ?: 0L
    private val confirmPending: Boolean = savedStateHandle.get<Boolean>("confirm") ?: false

    private val _state = MutableStateFlow(TxnEditUiState())
    val state: StateFlow<TxnEditUiState> = _state

    init {
        viewModelScope.launch {
            val accounts = repository.accounts.first()
            val categories = repository.categories.first()
            val txn = repository.getTxn(txnId)
            if (txn == null) {
                _state.update { it.copy(loading = false, exists = false) }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    confirmMode = confirmPending,
                    amountText = BigDecimal.valueOf(txn.amount)
                        .movePointLeft(2)
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString(),
                    type = txn.type,
                    categoryId = txn.categoryId,
                    accountId = txn.accountId,
                    merchant = txn.merchant.orEmpty(),
                    note = txn.note.orEmpty(),
                    occurredAt = txn.occurredAt,
                    accounts = accounts,
                    categories = categories,
                    source = txn.source,
                    editCount = txn.editCount
                )
            }
        }
        viewModelScope.launch {
            repository.observeEditLogs(txnId).collect { logs ->
                _state.update { it.copy(logs = logs) }
            }
        }
    }

    fun setAmount(text: String) {
        _state.update { it.copy(amountText = text, error = null) }
    }

    fun setType(type: String) {
        _state.update { current ->
            val default = current.categories.firstOrNull { it.type == type }
            current.copy(type = type, categoryId = default?.id ?: current.categoryId)
        }
    }

    fun selectCategory(id: Long) {
        _state.update { it.copy(categoryId = id) }
    }

    fun selectAccount(id: Long) {
        _state.update { it.copy(accountId = id) }
    }

    fun setMerchant(text: String) {
        _state.update { it.copy(merchant = text) }
    }

    fun setNote(text: String) {
        _state.update { it.copy(note = text) }
    }

    fun setOccurredAt(millis: Long) {
        _state.update { it.copy(occurredAt = millis) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val amount = current.amountText.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.movePointRight(2)
            ?.toLong()
            ?.takeIf { it > 0 }
        if (amount == null) {
            _state.update { it.copy(error = "请输入有效金额") }
            return
        }
        val accountId = current.accountId
        if (accountId == null) {
            _state.update { it.copy(error = "请选择账户") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repository.updateTransaction(
                id = txnId,
                type = current.type,
                amount = amount,
                accountId = accountId,
                categoryId = current.categoryId,
                merchant = current.merchant,
                note = current.note,
                occurredAt = current.occurredAt
            )
            if (current.confirmMode) {
                repository.confirmPendingTransaction(txnId)
            }
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun delete() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.softDelete(txnId)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }
}
