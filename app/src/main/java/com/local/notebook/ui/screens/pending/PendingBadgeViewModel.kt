package com.local.notebook.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PendingBadgeViewModel @Inject constructor(
    repository: LedgerRepository
) : ViewModel() {
    val count: StateFlow<Int> = repository.pendingCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )
}
