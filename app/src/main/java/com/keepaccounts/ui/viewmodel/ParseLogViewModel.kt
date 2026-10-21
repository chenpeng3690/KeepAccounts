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
    MATCHED("已入账"),
    PENDING("待确认"),
    REJECTED("已拦截")
}

data class ParseLogUiState(
    val items: List<RawEventEntity> = emptyList(),
    val filter: LogFilter = LogFilter.ALL,
    /** null = 全部应用 */
    val appFilter: String? = null,
    val apps: List<String> = emptyList(),
    val loaded: Boolean = false
)

class ParseLogViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(LogFilter.ALL)
    private val appFilter = MutableStateFlow<String?>(null)

    private val recent = container.db.rawEventDao().observeRecent(500)

    val state: StateFlow<ParseLogUiState> = combine(recent, filter, appFilter) { events, current, app ->
        val statusFiltered = when (current) {
            LogFilter.ALL -> events
            LogFilter.MATCHED -> events.filter { it.parseStatus == "matched" }
            LogFilter.PENDING -> events.filter { it.parseStatus == "pending" }
            // 已拦截 = 噪音拦截 + 未命中规则，都是「没记成账」
            LogFilter.REJECTED -> events.filter {
                it.parseStatus == "rejected" || it.parseStatus == "unmatched"
            }
        }
        val items = if (app == null) statusFiltered
        else statusFiltered.filter { (it.appLabel ?: it.packageName) == app }
        ParseLogUiState(
            items = items,
            filter = current,
            appFilter = app,
            apps = events.map { it.appLabel ?: it.packageName }.distinct(),
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

    fun setAppFilter(app: String?) {
        appFilter.value = app
    }
}
