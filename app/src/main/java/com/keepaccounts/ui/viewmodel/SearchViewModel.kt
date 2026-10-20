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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 时间范围筛选 */
enum class SearchRange(val label: String) {
    THIS_MONTH("本月"),
    LAST_MONTH("上月"),
    LAST_90("近三个月"),
    ALL("全部")
}

data class SearchUiState(
    val keyword: String = "",
    val typeFilter: String = "",
    val accountFilter: Long = 0L,
    val range: SearchRange = SearchRange.THIS_MONTH,
    val results: List<TransactionEntity> = emptyList(),
    val total: Long = 0,
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val accounts: Map<Long, AccountEntity> = emptyMap()
)

private data class SearchFilters(
    val keyword: String,
    val type: String,
    val accountId: Long,
    val range: SearchRange
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val keyword = MutableStateFlow("")
    private val typeFilter = MutableStateFlow("")
    private val accountFilter = MutableStateFlow(0L)
    private val range = MutableStateFlow(SearchRange.THIS_MONTH)

    private val filters = combine(keyword, typeFilter, accountFilter, range) { k, t, a, r ->
        SearchFilters(k.trim(), t, a, r)
    }

    val state: StateFlow<SearchUiState> = combine(
        container.txnRepo.observeActive(),
        container.categoryRepo.observeAll(),
        container.accountRepo.observeActive(),
        filters
    ) { txns, categories, accounts, f ->
        val now = System.currentTimeMillis()
        val (from, to) = rangeBounds(f.range, now)

        val results = txns
            .filter { it.status == TxStatus.CONFIRMED }
            .filter { it.occurredAt in from..to }
            .filter { t ->
                f.type.isEmpty() ||
                    (f.type == TxType.EXPENSE && t.type == TxType.EXPENSE) ||
                    (f.type == TxType.INCOME && (t.type == TxType.INCOME || t.type == TxType.REFUND))
            }
            .filter { t ->
                f.accountId == 0L || t.accountId == f.accountId || t.toAccountId == f.accountId
            }
            .filter { t ->
                f.keyword.isEmpty() ||
                    t.merchant?.contains(f.keyword, ignoreCase = true) == true ||
                    t.note?.contains(f.keyword, ignoreCase = true) == true
            }
            .sortedByDescending { it.occurredAt }

        SearchUiState(
            keyword = f.keyword,
            typeFilter = f.type,
            accountFilter = f.accountId,
            range = f.range,
            results = results,
            // 合计只算真实的收支，转账与余额调整不计入
            total = results.filter { it.type != TxType.TRANSFER }
                .filter { it.source != com.keepaccounts.data.local.db.TxSource.ADJUSTMENT }
                .sumOf { if (it.type == TxType.EXPENSE) -it.amount else it.amount },
            categories = categories.associateBy { it.id },
            accounts = accounts.associateBy { it.id }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun setKeyword(value: String) { keyword.value = value }

    fun setType(value: String) { typeFilter.value = value }

    fun setAccount(value: Long) { accountFilter.value = value }

    fun setRange(value: SearchRange) { range.value = value }

    private fun rangeBounds(range: SearchRange, now: Long): Pair<Long, Long> = when (range) {
        SearchRange.THIS_MONTH -> Time.startOfMonth(now) to Time.endOfMonth(now)
        SearchRange.LAST_MONTH -> {
            val lastMonthPoint = Time.startOfMonth(now) - 1
            Time.startOfMonth(lastMonthPoint) to Time.endOfMonth(lastMonthPoint)
        }
        SearchRange.LAST_90 -> Time.daysAgo(now, 90) to now
        SearchRange.ALL -> 0L to Long.MAX_VALUE
    }
}
