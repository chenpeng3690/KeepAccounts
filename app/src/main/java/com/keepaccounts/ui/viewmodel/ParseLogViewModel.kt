package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.RawEventEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class LogFilter(val label: String) {
    ALL("全部"),
    MATCHED("已记账"),
    PENDING("待确认"),
    REJECTED("被拦截"),
    UNMATCHED("未命中"),
    CAPTURED("未解析")
}

data class ParseLogUiState(
    val items: List<RawEventEntity> = emptyList(),
    val filter: LogFilter = LogFilter.ALL,
    val loaded: Boolean = false
)

class ParseLogViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(LogFilter.ALL)

    private val recent = container.db.rawEventDao().observeRecent(500)

    val state: StateFlow<ParseLogUiState> = combine(recent, filter) { events, current ->
        val status = when (current) {
            LogFilter.ALL -> null
            LogFilter.MATCHED -> "matched"
            LogFilter.PENDING -> "pending"
            LogFilter.REJECTED -> "rejected"
            LogFilter.UNMATCHED -> "unmatched"
            LogFilter.CAPTURED -> "captured"
        }
        ParseLogUiState(
            items = if (status == null) events else events.filter { it.parseStatus == status },
            filter = current,
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ParseLogUiState()
    )

    fun setFilter(value: LogFilter) {
        filter.value = value
    }
}
