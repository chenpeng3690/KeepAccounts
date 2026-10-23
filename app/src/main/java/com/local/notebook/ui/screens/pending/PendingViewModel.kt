package com.local.notebook.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.PendingRow
import com.local.notebook.data.local.RuleEntity
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingOverview(
    val autoPosted: Int = 0,
    val blocked: Int = 0,
    val pending: Int = 0,
    val rejected: List<NotificationRawEntity> = emptyList()
)

data class PendingUiState(
    val loading: Boolean = true,
    val items: List<PendingRow> = emptyList(),
    val overview: PendingOverview = PendingOverview()
)

@HiltViewModel
class PendingViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    private val readyRule = MutableStateFlow<RuleEntity?>(null)
    val ruleReady: StateFlow<RuleEntity?> = readyRule

    val state: StateFlow<PendingUiState> = combine(
        repository.pendingTxns,
        repository.rawLogs
    ) { pending, raws ->
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val week = raws.filter { it.receivedAt >= cutoff }
        PendingUiState(
            loading = false,
            items = pending,
            overview = PendingOverview(
                autoPosted = week.count { it.matchResult == "MATCHED" },
                blocked = week.count { it.matchResult == "REJECTED" },
                pending = pending.size,
                rejected = week.filter { it.matchResult == "REJECTED" }.take(5)
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PendingUiState()
    )

    fun confirm(id: Long) {
        viewModelScope.launch {
            val rule = repository.confirmPendingTransaction(id)
            maybeOfferEnable(rule)
        }
    }

    fun reject(id: Long) {
        viewModelScope.launch { repository.rejectPendingTransaction(id) }
    }

    fun confirmAll() {
        viewModelScope.launch {
            val rules = repository.confirmAllPending()
            rules.forEach { maybeOfferEnable(it) }
        }
    }

    fun rejectAll() {
        viewModelScope.launch { repository.rejectAllPending() }
    }

    fun enableRule(ruleId: Long) {
        viewModelScope.launch {
            repository.setRuleState(ruleId, "ACTIVE")
            readyRule.value = null
        }
    }

    fun dismissRuleOffer() {
        readyRule.value = null
    }

    private fun maybeOfferEnable(rule: RuleEntity?) {
        if (rule == null) return
        if (rule.state == "OBSERVING" &&
            rule.statsConfirmed >= 3 &&
            rule.statsRejected == 0
        ) {
            readyRule.value = rule
        }
    }
}
