package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.ParseStatus
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.data.local.db.RuleState
import com.keepaccounts.data.local.db.TransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 待确认箱里的一条：流水 + 用于对照的原文 + 来源 + 已解析的分类/账户 */
data class PendingItem(
    val txn: TransactionEntity,
    val rawText: String?,
    val appLabel: String?,
    val source: String,
    val isObserving: Boolean,
    val category: CategoryEntity?,
    val account: AccountEntity?
)

data class PendingUiState(
    val items: List<PendingItem> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val weekMatched: Int = 0,
    val weekRejected: Int = 0,
    val weekUnmatched: Int = 0,
    val weekCaptured: Int = 0,
    val recentRejects: List<RawEventEntity> = emptyList(),
    val loaded: Boolean = false
)

class PendingViewModel(private val container: AppContainer) : ViewModel() {

    private val parseRepo = container.parseRepo

    private val weekAgo = System.currentTimeMillis() - 7 * 24 * 3600_000L

    val state: StateFlow<PendingUiState> = combine(
        container.txnRepo.observePending(),
        container.db.ruleDao().observeAll(),
        container.categoryRepo.observeAll(),
        container.accountRepo.observeActive(),
        container.db.rawEventDao().observeRecent(500)
    ) { pending, rules, categories, accounts, rawEvents ->
        val ruleById = rules.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        val accountById = accounts.associateBy { it.id }
        val rawById = rawEvents.associateBy { it.id }

        val items = pending.map { txn ->
            val raw = txn.rawEventId?.let { rawById[it] }
            val rule = txn.ruleId?.let { ruleById[it] }
            PendingItem(
                txn = txn,
                rawText = raw?.text,
                appLabel = raw?.appLabel ?: raw?.packageName,
                source = when {
                    rule == null -> "通用兜底"
                    rule.state == RuleState.OBSERVING -> "${rule.name} · 观察中"
                    else -> rule.name
                },
                isObserving = rule?.state == RuleState.OBSERVING,
                category = txn.categoryId?.let { categoryById[it] },
                account = accountById[txn.accountId]
            )
        }

        val week = rawEvents.filter { it.receivedAt >= weekAgo }

        PendingUiState(
            items = items,
            categories = categories,
            accounts = accounts,
            weekMatched = week.count { it.parseStatus == ParseStatus.MATCHED },
            weekRejected = week.count { it.parseStatus == ParseStatus.REJECTED },
            weekUnmatched = week.count { it.parseStatus == ParseStatus.UNMATCHED },
            weekCaptured = week.count { it.parseStatus == ParseStatus.CAPTURED },
            recentRejects = week.filter { it.parseStatus == ParseStatus.REJECTED }.take(5),
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PendingUiState()
    )

    fun confirm(id: Long) {
        viewModelScope.launch { runCatching { parseRepo.confirmPending(id) } }
    }

    fun confirmEdited(id: Long, patch: (TransactionEntity) -> TransactionEntity) {
        viewModelScope.launch { runCatching { parseRepo.confirmPending(id, patch) } }
    }

    fun reject(id: Long) {
        viewModelScope.launch { runCatching { parseRepo.rejectPending(id) } }
    }

    fun confirmAll() {
        viewModelScope.launch {
            val ids = state.value.items.map { it.txn.id }
            runCatching { parseRepo.confirmAll(ids) }
        }
    }
}
