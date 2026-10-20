package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class RawFilter(val label: String) {
    ALL("全部"),
    WITH_AMOUNT("含金额"),
    NO_AMOUNT("不含金额")
}

data class RawEventsUiState(
    val items: List<RawEventEntity> = emptyList(),
    val total: Int = 0,
    val withAmount: Int = 0,
    val lastReceivedAt: Long? = null,
    val filter: RawFilter = RawFilter.ALL,
    val loaded: Boolean = false
)

class RawEventsViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(RawFilter.ALL)

    private val recent = container.db.rawEventDao().observeRecent(500)

    val state: StateFlow<RawEventsUiState> = combine(recent, filter) { events, current ->
        val amountFlags = events.associate { it.id to (Money.extractFirstAmount(it.text) != null) }
        val filtered = when (current) {
            RawFilter.ALL -> events
            RawFilter.WITH_AMOUNT -> events.filter { amountFlags[it.id] == true }
            RawFilter.NO_AMOUNT -> events.filter { amountFlags[it.id] != true }
        }
        RawEventsUiState(
            items = filtered,
            total = events.size,
            withAmount = amountFlags.values.count { it },
            lastReceivedAt = events.maxOfOrNull { it.receivedAt },
            filter = current,
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RawEventsUiState()
    )

    fun setFilter(value: RawFilter) {
        filter.value = value
    }

    suspend fun clearAll() {
        container.db.rawEventDao().purgeBefore(Long.MAX_VALUE)
    }
}
