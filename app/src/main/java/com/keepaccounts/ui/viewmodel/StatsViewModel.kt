package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.BudgetEntity
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分类占比的一瓣。category 为 null 表示「未分类」 */
data class CategorySlice(
    val category: CategoryEntity?,
    val amount: Long,
    val percent: Float = 0f
)

data class StatsUiState(
    val month: Long = Time.startOfMonth(System.currentTimeMillis()),
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val slices: List<CategorySlice> = emptyList(),
    val budget: BudgetEntity? = null,
    /** 近 7 天每日支出 (当日 0 点时间戳, 支出分) */
    val trend: List<Pair<Long, Long>> = emptyList(),
    val maxTrend: Long = 1,
    val loaded: Boolean = false
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {

    private val month = MutableStateFlow(Time.startOfMonth(System.currentTimeMillis()))
    private val budgetDao = container.db.budgetDao()

    val state: StateFlow<StatsUiState> = combine(
        month,
        container.txnRepo.observeActive(),
        container.categoryRepo.observeAll(),
        budgetDao.observeEnabled()
    ) { m, txns, categories, budgets ->
        val start = Time.startOfMonth(m)
        val end = Time.endOfMonth(m)
        val catById = categories.associateBy { it.id }

        // 只算真实收支：排除 transfer、余额调整、待确认、软删除
        val inMonth = txns.filter {
            it.occurredAt in start..end &&
                it.status == TxStatus.CONFIRMED &&
                it.source != TxSource.ADJUSTMENT &&
                it.type != TxType.TRANSFER
        }

        val expense = inMonth.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
        val income = inMonth.filter { it.type == TxType.INCOME }.sumOf { it.amount }

        // 分类占比（只算支出）；未分类的归为一个「未分类」灰条
        val expenseTxns = inMonth.filter { it.type == TxType.EXPENSE }
        val byCat = expenseTxns
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val uncategorized = byCat[null] ?: 0L
        val total = expense
        val slices = mutableListOf<CategorySlice>()
        byCat.filterKeys { it != null }.forEach { (cid, amt) ->
            val cat = catById[cid]
            if (cat != null) slices.add(CategorySlice(cat, amt))
        }
        if (uncategorized > 0) slices.add(CategorySlice(null, uncategorized))
        slices.sortByDescending { it.amount }
        slices.forEachIndexed { i, s ->
            slices[i] = s.copy(percent = if (total > 0) s.amount.toFloat() / total else 0f)
        }

        // 本月总预算（按 startDate 精确匹配该月月初）
        val budget = budgets.firstOrNull {
            it.scope == "total" && it.period == "month" && it.startDate == start
        }

        // 近 7 天趋势（含今天）；用全量真实支出，避免月初跨月时数据断档
        val realExpense = txns.filter {
            it.type == TxType.EXPENSE && it.status == TxStatus.CONFIRMED &&
                it.source != TxSource.ADJUSTMENT
        }
        val today = Time.toDate(System.currentTimeMillis())
        val trend = (6 downTo 0).map { offset ->
            val dayStart = Time.fromDate(today.minusDays(offset.toLong()))
            val dayEnd = dayStart + 24 * 3600_000L - 1
            val amt = realExpense.filter { it.occurredAt in dayStart..dayEnd }.sumOf { it.amount }
            dayStart to amt
        }
        val maxTrend = trend.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

        StatsUiState(
            month = m,
            monthExpense = expense,
            monthIncome = income,
            slices = slices,
            budget = budget,
            trend = trend,
            maxTrend = maxTrend,
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    fun shiftMonth(delta: Int) {
        month.update { m -> Time.fromDate(Time.toDate(m).plusMonths(delta.toLong())) }
    }

    fun setBudget(amount: Long) {
        viewModelScope.launch {
            val start = Time.startOfMonth(month.value)
            val existing = budgetDao.listEnabled()
                .firstOrNull { it.scope == "total" && it.period == "month" && it.startDate == start }
            if (existing != null) {
                budgetDao.update(existing.copy(amount = amount))
            } else {
                budgetDao.insert(
                    BudgetEntity(
                        scope = "total",
                        period = "month",
                        amount = amount,
                        startDate = start,
                        enabled = true
                    )
                )
            }
        }
    }

    fun clearBudget() {
        viewModelScope.launch {
            val start = Time.startOfMonth(month.value)
            budgetDao.listEnabled()
                .firstOrNull { it.scope == "total" && it.period == "month" && it.startDate == start }
                ?.let { budgetDao.delete(it.id) }
        }
    }
}
