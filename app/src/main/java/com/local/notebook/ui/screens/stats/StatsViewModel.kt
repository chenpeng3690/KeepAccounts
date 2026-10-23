package com.local.notebook.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.BudgetEntity
import com.local.notebook.data.local.TxnRow
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

data class CategorySliceUi(
    val name: String,
    val amount: Long,
    val percent: Int
)

data class DayPointUi(
    val date: LocalDate,
    val amount: Long
)

data class BudgetStatsUi(
    val amount: Long,
    val spent: Long,
    val remaining: Long,
    val over: Boolean,
    val dailyAvailable: Long,
    val daysLeft: Int,
    val progress: Float
)

data class StatsUiState(
    val loading: Boolean = true,
    val monthLabel: String = "",
    val income: Long = 0,
    val expense: Long = 0,
    val balance: Long = 0,
    val composition: String = "EXPENSE",
    val categories: List<CategorySliceUi> = emptyList(),
    val trend: List<DayPointUi> = emptyList(),
    val budget: BudgetStatsUi? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val composition = MutableStateFlow("EXPENSE")

    val state: StateFlow<StatsUiState> = combine(
        repository.activeTxns,
        repository.budget,
        month,
        composition
    ) { txns, budget, selected, mode ->
        buildState(txns, budget, selected, mode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (next <= YearMonth.now()) month.value = next
    }

    fun toggleComposition() {
        composition.value = if (composition.value == "EXPENSE") "INCOME" else "EXPENSE"
    }

    fun setBudget(cents: Long) {
        if (cents <= 0) return
        viewModelScope.launch { repository.setTotalMonthBudget(cents) }
    }

    private fun buildState(
        txns: List<TxnRow>,
        budget: BudgetEntity?,
        selected: YearMonth,
        mode: String
    ): StatsUiState {
        val zone = ZoneId.systemDefault()
        val start = selected.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = selected.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val monthRows = txns.filter {
            it.occurredAt in start until end &&
                (it.type == "EXPENSE" || it.type == "INCOME") &&
                it.source != "ADJUSTMENT"
        }
        val expense = monthRows.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val income = monthRows.filter { it.type == "INCOME" }.sumOf { it.amount }

        val compositionTotal = if (mode == "INCOME") income else expense
        val byCategory = monthRows
            .filter { it.type == mode }
            .groupBy { it.categoryName ?: "未分类" }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val categories = when {
            byCategory.size <= 5 -> byCategory.map { (name, amount) ->
                CategorySliceUi(
                    name = name,
                    amount = amount,
                    percent = if (compositionTotal > 0) {
                        (amount * 100f / compositionTotal).roundToInt()
                    } else {
                        0
                    }
                )
            }
            else -> {
                val top = byCategory.take(4)
                val other = byCategory.drop(4).sumOf { it.second }
                top.map { (name, amount) ->
                    CategorySliceUi(
                        name = name,
                        amount = amount,
                        percent = (amount * 100f / compositionTotal).roundToInt()
                    )
                } + CategorySliceUi(
                    name = "其他",
                    amount = other,
                    percent = (other * 100f / compositionTotal).roundToInt()
                )
            }
        }

        val trendEnd = if (selected == YearMonth.now()) {
            LocalDate.now()
        } else {
            selected.atEndOfMonth()
        }
        val trendStart = trendEnd.minusDays(6)
        val daily = (0..6).map { offset ->
            val date = trendStart.plusDays(offset.toLong())
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val amount = monthRows.filter {
                it.type == "EXPENSE" && it.occurredAt in dayStart until dayEnd
            }.sumOf { it.amount }
            DayPointUi(date, amount)
        }

        val budgetUi = budget?.takeIf { it.amount > 0 }?.let {
            val remaining = max(it.amount - expense, 0)
            val today = LocalDate.now()
            val daysLeft = if (selected == YearMonth.now()) {
                max(selected.lengthOfMonth() - today.dayOfMonth + 1, 1)
            } else {
                1
            }
            BudgetStatsUi(
                amount = it.amount,
                spent = expense,
                remaining = remaining,
                over = expense > it.amount,
                dailyAvailable = remaining / daysLeft,
                daysLeft = daysLeft,
                progress = (expense.toFloat() / it.amount).coerceIn(0f, 1f)
            )
        }

        return StatsUiState(
            loading = false,
            monthLabel = "${selected.year} 年 ${selected.monthValue} 月",
            income = income,
            expense = expense,
            balance = income - expense,
            composition = mode,
            categories = categories,
            trend = daily,
            budget = budgetUi
        )
    }
}
