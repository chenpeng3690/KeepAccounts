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

    /**
     * 点击切换：已启用 → 停用；已停用 → 启用；观察中 → 保持不变
     * （观察中的规则要「确认正确」攒够 3 条才启用，不能一点就启用）。
     */
    fun toggle(id: Long, currentState: String) {
        val next = when (currentState) {
            RuleState.ACTIVE -> RuleState.DISABLED
            RuleState.DISABLED -> RuleState.ACTIVE
            else -> null // OBSERVING：点击不变，靠长按「立即启用」
        }
        if (next != null) setState(id, next)
    }

    /** 观察中 → 立即启用（长按菜单里的选项） */
    fun activate(id: Long) = setState(id, RuleState.ACTIVE)

    /** 删除自定义规则（内置规则不可删） */
    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { ruleDao.deleteCustom(id) }
        }
    }
}
