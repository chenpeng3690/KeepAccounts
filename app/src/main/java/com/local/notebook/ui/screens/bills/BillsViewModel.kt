package com.local.notebook.ui.screens.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.BudgetEntity
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.data.local.TxnRow
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class DayGroupUi(
    val label: String,
    val expenseTotal: Long,
    val rows: List<TxnRow>
)

data class BudgetUi(
    val amount: Long,
    val spent: Long,
    val progress: Float,
    val remaining: Long,
    val over: Boolean
)

data class BillAccountOption(
    val id: Long,
    val name: String
)

data class BillCategoryOption(
    val id: Long,
    val name: String,
    val type: String
)

data class BillsFilter(
    val month: YearMonth = YearMonth.now(),
    val keyword: String = "",
    val type: String? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val source: String? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null
) {
    val hasActiveConditions: Boolean
        get() = keyword.isNotBlank() || type != null || categoryId != null ||
            accountId != null || source != null || minCents != null || maxCents != null
}

data class BillsUiState(
    val loading: Boolean = true,
    val monthLabel: String = "",
    val monthText: String = "",
    val isCurrentMonth: Boolean = true,
    val expense: Long = 0,
    val income: Long = 0,
    val balance: Long = 0,
    val groups: List<DayGroupUi> = emptyList(),
    val budget: BudgetUi? = null,
    val accounts: List<BillAccountOption> = emptyList(),
    val categories: List<BillCategoryOption> = emptyList(),
    val filter: BillsFilter = BillsFilter(),
    val hasActiveFilters: Boolean = false,
    val resultCount: Int = 0
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    private val filter = MutableStateFlow(BillsFilter())

    val uiState: StateFlow<BillsUiState> = combine(
        repository.activeTxns,
        repository.budget,
        repository.accounts,
        repository.categories,
        filter
    ) { txns, budget, accounts, categories, currentFilter ->
        buildState(txns, budget, accounts, categories, currentFilter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BillsUiState()
    )

    suspend fun softDelete(id: Long) = repository.softDelete(id)

    suspend fun restore(id: Long) = repository.restore(id)

    fun previousMonth() {
        filter.update { it.copy(month = it.month.minusMonths(1)) }
    }

    fun nextMonth() {
        filter.update {
            val next = it.month.plusMonths(1)
            if (next <= YearMonth.now()) it.copy(month = next) else it
        }
    }

    fun setMonth(month: YearMonth) {
        filter.update { it.copy(month = minOf(month, YearMonth.now())) }
    }

    fun goToCurrentMonth() {
        setMonth(YearMonth.now())
    }

    fun setKeyword(value: String) {
        filter.update { it.copy(keyword = value) }
    }

    fun setType(type: String?) {
        filter.update { it.copy(type = type, categoryId = null) }
    }

    fun setCategory(categoryId: Long?) {
        filter.update { it.copy(categoryId = categoryId) }
    }

    fun setAccount(accountId: Long?) {
        filter.update { it.copy(accountId = accountId) }
    }

    fun setSource(source: String?) {
        filter.update { it.copy(source = source) }
    }

    fun setAmountRange(minCents: Long?, maxCents: Long?) {
        filter.update { it.copy(minCents = minCents, maxCents = maxCents) }
    }

    fun clearFilters() {
        filter.update { BillsFilter(month = it.month) }
    }

    private fun buildState(
        txns: List<TxnRow>,
        budget: BudgetEntity?,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        current: BillsFilter
    ): BillsUiState {
        val selected = current.month
        val now = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val start = selected.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = selected.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val monthRows = txns.filter { it.occurredAt in start until end }
        val keyword = current.keyword.trim()
        val minCents = current.minCents
        val maxCents = current.maxCents
        val rows = monthRows.filter { row ->
            matchesType(row, current.type) &&
                (current.categoryId == null || row.categoryId == current.categoryId) &&
                (current.accountId == null ||
                    row.accountId == current.accountId ||
                    row.toAccountId == current.accountId) &&
                (current.source == null || row.source == current.source) &&
                (minCents == null || row.amount >= minCents) &&
                (maxCents == null || row.amount <= maxCents) &&
                matchesKeyword(row, keyword)
        }

        val counted = rows.filter {
            (it.type == "EXPENSE" || it.type == "INCOME") && it.source != "ADJUSTMENT"
        }
        val expense = counted.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val income = counted.filter { it.type == "INCOME" }.sumOf { it.amount }

        val groups = rows
            .groupBy { row ->
                Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayRows) ->
                DayGroupUi(
                    label = dayLabel(date),
                    expenseTotal = dayRows.filter {
                        it.type == "EXPENSE" && it.source != "ADJUSTMENT"
                    }.sumOf { it.amount },
                    rows = dayRows.sortedByDescending { it.occurredAt }
                )
            }

        val hasActiveFilters = current.hasActiveConditions
        val monthExpense = monthRows.filter {
            it.type == "EXPENSE" && it.source != "ADJUSTMENT"
        }.sumOf { it.amount }
        val budgetUi = budget
            ?.takeIf { it.amount > 0 && selected == now && !hasActiveFilters }
            ?.let {
                BudgetUi(
                    amount = it.amount,
                    spent = monthExpense,
                    progress = (monthExpense.toFloat() / it.amount).coerceIn(0f, 1f),
                    remaining = (it.amount - monthExpense).coerceAtLeast(0),
                    over = monthExpense > it.amount
                )
            }

        return BillsUiState(
            loading = false,
            monthLabel = "${selected.monthValue} 月${monthTypeLabel(current.type)}",
            monthText = "${selected.year} 年 ${selected.monthValue} 月",
            isCurrentMonth = selected == now,
            expense = expense,
            income = income,
            balance = income - expense,
            groups = groups,
            budget = budgetUi,
            accounts = accounts
                .filterNot { it.archived }
                .map { BillAccountOption(id = it.id, name = it.name) },
            categories = categories.map {
                BillCategoryOption(id = it.id, name = it.name, type = it.type)
            },
            filter = current,
            hasActiveFilters = hasActiveFilters,
            resultCount = rows.size
        )
    }

    private fun matchesType(row: TxnRow, type: String?): Boolean =
        type == null || row.type == type

    private fun matchesKeyword(row: TxnRow, keyword: String): Boolean {
        if (keyword.isEmpty()) return true
        return listOfNotNull(
            row.merchant,
            row.note,
            row.categoryName,
            row.accountName,
            row.toAccountName
        ).any { it.contains(keyword, ignoreCase = true) }
    }

    private fun monthTypeLabel(type: String?): String = when (type) {
        "INCOME" -> "收入"
        "TRANSFER" -> "转账"
        else -> "支出"
    }

    private fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> if (date.year == today.year) {
                date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
            } else {
                date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
            }
        }
    }
}
