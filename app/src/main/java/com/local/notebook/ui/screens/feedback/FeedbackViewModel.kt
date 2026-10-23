package com.local.notebook.ui.screens.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.service.feedback.FeedbackBus
import com.local.notebook.service.feedback.FeedbackEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    bus: FeedbackBus,
    private val repository: LedgerRepository
) : ViewModel() {

    val events: SharedFlow<FeedbackEvent> = bus.events

    fun undo(txnId: Long) {
        viewModelScope.launch { repository.softDelete(txnId) }
    }
}
