package com.local.notebook.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class LogFilter(val label: String) {
    ALL("全部"),
    MATCHED("已入账"),
    PENDING("待确认"),
    IGNORED("已忽略"),
    FAILED("AI 失败")
}

@HiltViewModel
class ParseLogViewModel @Inject constructor(
    repository: LedgerRepository
) : ViewModel() {

    private val filter = MutableStateFlow(LogFilter.ALL)

    val logs: StateFlow<List<NotificationRawEntity>> = combine(
        repository.rawLogs,
        filter
    ) { logs, current ->
        when (current) {
            LogFilter.ALL -> logs
            LogFilter.MATCHED -> logs.filter {
                it.matchResult == "MATCHED" || it.matchResult == "AI_MATCHED"
            }
            LogFilter.PENDING -> logs.filter {
                it.matchResult in setOf("PENDING", "AI_PENDING", "QUEUED", "AI_RETRY")
            }
            LogFilter.IGNORED -> logs.filter {
                it.matchResult in setOf("REJECTED", "AI_IGNORED", "AI_DUPLICATE")
            }
            LogFilter.FAILED -> logs.filter { it.matchResult == "AI_FAILED" }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val currentFilter: StateFlow<LogFilter> = filter

    fun setFilter(value: LogFilter) {
        filter.value = value
    }
}
