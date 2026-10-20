package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.util.Time
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DayGroup(
    val dayStart: Long,
    val label: String,
    val expense: Long,
    val income: Long,
    val items: List<TransactionEntity>
)

data class LedgerUiState(
    val groups: List<DayGroup> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val accounts: Map<Long, AccountEntity> = emptyMap(),
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val loaded: Boolean = false
)

class LedgerViewModel(container: AppContainer) : ViewModel() {

    val state: StateFlow<LedgerUiState> = combine(
        container.txnRepo.observeActive(),
        container.categoryRepo.observeAll(),
        container.accountRepo.observeActive(),
        container.txnRepo.observeMonthExpense(),
        container.txnRepo.observeMonthIncome()
    ) { txns, categories, accounts, expense, income ->
        // 待确认的账单独待在「待确认」Tab 里，不混进账单列表
        val confirmed = txns.filter { it.status == TxStatus.CONFIRMED }

        val groups = confirmed
            .groupBy { Time.startOfDay(it.occurredAt) }
            .toList()
            .sortedByDescending { it.first }
            .map { (day, items) ->
                DayGroup(
                    dayStart = day,
                    label = Time.dayLabel(day),
                    expense = items.filter { it.type == TxType.EXPENSE }
                        .sumOf { it.amount },
                    income = items.filter { it.type == TxType.INCOME || it.type == TxType.REFUND }
                        .sumOf { it.amount },
                    items = items.sortedByDescending { it.occurredAt }
                )
            }

        LedgerUiState(
            groups = groups,
            categories = categories.associateBy { it.id },
            accounts = accounts.associateBy { it.id },
            monthExpense = expense,
            monthIncome = income,
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState()
    )
}
