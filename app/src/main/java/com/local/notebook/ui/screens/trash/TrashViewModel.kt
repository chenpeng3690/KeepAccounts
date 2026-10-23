package com.local.notebook.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.TxnRow
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val deleted: StateFlow<List<TxnRow>> = repository.deletedTxns.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val retentionDays: StateFlow<Int> = settings.trashRetentionDays.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 7
    )

    init {
        viewModelScope.launch {
            repository.purgeExpired(settings.trashRetentionDays.first())
        }
    }

    suspend fun restore(id: Long) = repository.restore(id)

    suspend fun purge(id: Long) = repository.purge(id)

    suspend fun clearAll() = repository.clearTrash()

    fun setRetention(days: Int) {
        viewModelScope.launch {
            settings.setTrashRetentionDays(days)
            repository.purgeExpired(days)
        }
    }
}
