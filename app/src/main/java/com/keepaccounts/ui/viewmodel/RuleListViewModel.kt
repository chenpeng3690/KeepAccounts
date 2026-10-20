package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.RuleEntity
import com.keepaccounts.data.local.db.RuleState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RuleListUiState(
    val rules: List<RuleEntity> = emptyList(),
    val loaded: Boolean = false
)

class RuleListViewModel(private val container: AppContainer) : ViewModel() {

    private val ruleDao = container.db.ruleDao()

    val state: StateFlow<RuleListUiState> = ruleDao.observeAll()
        .map { RuleListUiState(rules = it, loaded = true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RuleListUiState()
        )

    fun setState(id: Long, state: String) {
        viewModelScope.launch {
            runCatching { ruleDao.setState(id, state, System.currentTimeMillis()) }
        }
    }

    /** 开关：ACTIVE <-> DISABLED */
    fun toggle(id: Long, currentlyActive: Boolean) {
        setState(id, if (currentlyActive) RuleState.DISABLED else RuleState.ACTIVE)
    }
}
