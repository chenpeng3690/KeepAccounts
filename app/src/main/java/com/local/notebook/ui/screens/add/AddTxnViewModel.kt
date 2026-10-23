package com.local.notebook.ui.screens.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.domain.util.AmountExpression
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddTxnUiState(
    val type: String = "EXPENSE",
    val expression: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null
) {
    val amountPreview: String
        get() {
            if (expression.isEmpty()) return "0.00"
            val value = AmountExpression.evaluate(expression)
            return if (value != null) {
                value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            } else {
                expression
            }
        }
}

@HiltViewModel
class AddTxnViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AddTxnUiState())
    val state: StateFlow<AddTxnUiState> = _state

    init {
        viewModelScope.launch {
            repository.accounts.collect { accounts ->
                _state.update { current ->
                    current.copy(
                        accounts = accounts,
                        accountId = current.accountId ?: accounts.firstOrNull()?.id
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.categories.collect { categories ->
                _state.update { current ->
                    val default = categories.firstOrNull {
                        it.type == current.type && it.name == preferredCategory(current.type)
                    } ?: categories.firstOrNull { it.type == current.type }
                    current.copy(
                        categories = categories,
                        categoryId = current.categoryId ?: default?.id
                    )
                }
            }
        }
    }

    fun setType(type: String) {
        _state.update { current ->
            val default = current.categories.firstOrNull {
                it.type == type && it.name == preferredCategory(type)
            } ?: current.categories.firstOrNull { it.type == type }
            current.copy(type = type, categoryId = default?.id, error = null)
        }
    }

    fun onKey(key: String) {
        _state.update { current ->
            val expression = current.expression
            val next = when {
                key == "del" -> expression.dropLast(1)
                key in setOf("+", "-", "×", "÷") -> {
                    when {
                        expression.isEmpty() -> expression
                        expression.last() in "+-×÷" -> expression.dropLast(1) + key
                        else -> expression + key
                    }
                }
                key == "." -> {
                    val lastNumber = expression.split(Regex("[+\\-×÷]")).lastOrNull().orEmpty()
                    when {
                        lastNumber.contains('.') -> expression
                        lastNumber.isEmpty() -> expression + "0."
                        else -> expression + "."
                    }
                }
                else -> {
                    val lastNumber = expression.split(Regex("[+\\-×÷]")).lastOrNull().orEmpty()
                    if (lastNumber.replace(".", "").length >= 9) expression else expression + key
                }
            }
            current.copy(expression = next.take(18), error = null)
        }
    }

    fun clearAmount() {
        _state.update { it.copy(expression = "", error = null) }
    }

    fun selectCategory(id: Long) {
        _state.update { it.copy(categoryId = id) }
    }

    fun selectAccount(id: Long) {
        _state.update { it.copy(accountId = id) }
    }

    fun setOccurredAt(millis: Long) {
        _state.update { it.copy(occurredAt = millis) }
    }

    fun setNote(note: String) {
        _state.update { it.copy(note = note) }
    }

    fun save(keepGoing: Boolean) {
        val current = _state.value
        if (current.saving) return
        val amount = AmountExpression.evaluate(current.expression)
            ?.let(AmountExpression::toCents)
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
            repository.addTransaction(
                type = current.type,
                amount = amount,
                accountId = accountId,
                categoryId = current.categoryId,
                merchant = null,
                note = current.note,
                occurredAt = current.occurredAt
            )
            if (keepGoing) {
                _state.update {
                    it.copy(
                        expression = "",
                        note = "",
                        occurredAt = System.currentTimeMillis(),
                        saving = false
                    )
                }
            } else {
                _state.update { it.copy(saving = false, finished = true) }
            }
        }
    }

    private fun preferredCategory(type: String): String =
        if (type == "INCOME") "工资" else "餐饮"
}
