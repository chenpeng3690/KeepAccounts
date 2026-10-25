package com.local.notebook.ui.screens.keywords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.TransactionKeywordEntity
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeywordViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    val keywords: StateFlow<List<TransactionKeywordEntity>> = repository.keywords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun toggle(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setKeywordEnabled(id, enabled) }
    }

    fun add(word: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.addKeyword(word)
            onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteKeyword(id) }
    }

    fun restoreDefaults() {
        viewModelScope.launch { repository.restoreDefaultKeywords() }
    }
}
