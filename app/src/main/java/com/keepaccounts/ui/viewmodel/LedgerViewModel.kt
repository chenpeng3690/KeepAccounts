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
import kotlinx.coroutines.flow.map
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
    /** 本月总预算；没设预算时为 null，首页就不显示预算条 */
    val budgetTotal: Long? = null,
    val loaded: Boolean = false
)

class LedgerViewModel(container: AppContainer) : ViewModel() {

    /** 本月总预算。预算模块在第 8 步落地，这里先把读取路径接好，没有数据就不显示预算条 */
    private val monthBudget = container.db.budgetDao().observeEnabled()
        .map { budgets ->
            budgets.firstOrNull { it.scope == "total" && it.period == "month" && it.enabled }?.amount
        }

    private data class MonthFigures(
        val expense: Long,
        val income: Long,
        val budget: Long?
    )

    private val monthFigures = combine(
        container.txnRepo.observeMonthExpense(),
        container.txnRepo.observeMonthIncome(),
        monthBudget
    ) { expense, income, budget -> MonthFigures(expense, income, budget) }

    val state: StateFlow<LedgerUiState> = combine(
        container.txnRepo.observeActive(),
        container.categoryRepo.observeAll(),
        container.accountRepo.observeActive(),
        monthFigures
    ) { txns, categories, accounts, figures ->
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
            monthExpense = figures.expense,
            monthIncome = figures.income,
            budgetTotal = figures.budget,
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState()
    )
}
