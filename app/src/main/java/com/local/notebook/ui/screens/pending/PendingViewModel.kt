package com.local.notebook.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.PendingRow
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

    val state: StateFlow<PendingUiState> = combine(
        repository.pendingTxns,
        repository.rawLogs
    ) { pending, raws ->
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val week = raws.filter { it.receivedAt >= cutoff }
        val matched = week.count { it.matchResult == "MATCHED" || it.matchResult == "AI_MATCHED" }
        val ignored = week.filter {
            it.matchResult == "REJECTED" || it.matchResult == "AI_IGNORED"
        }
        PendingUiState(
            loading = false,
            items = pending,
            overview = PendingOverview(
                autoPosted = matched,
                blocked = ignored.size,
                pending = pending.size,
                rejected = ignored.take(5)
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PendingUiState()
    )

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun confirm(id: Long) {
        viewModelScope.launch {
            val confirmed = repository.confirmPendingTransaction(id)
            _toast.value = if (confirmed) {
                "已确认入账"
            } else {
                "转账缺少转入账户，请先编辑补充"
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun reject(id: Long) {
        viewModelScope.launch { repository.rejectPendingTransaction(id) }
    }

    fun confirmAll() {
        viewModelScope.launch { repository.confirmAllPending() }
    }

    fun rejectAll() {
        viewModelScope.launch { repository.rejectAllPending() }
    }
}
