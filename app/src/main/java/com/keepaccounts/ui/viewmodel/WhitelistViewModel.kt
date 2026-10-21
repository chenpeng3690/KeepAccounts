package com.keepaccounts.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.SettingKeys
import com.keepaccounts.notification.InstalledApp
import com.keepaccounts.notification.NotificationGate
import com.keepaccounts.notification.loadLaunchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WhitelistUiState(
    val apps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val captureAll: Boolean = false,
    val genericRuleEnabled: Boolean = true,
    val query: String = "",
    val loaded: Boolean = false
) {
    val visibleApps: List<InstalledApp>
        get() = if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
}

class WhitelistViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(WhitelistUiState())
    val state: StateFlow<WhitelistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { loadLaunchableApps(appContext) }
            val selected = NotificationGate.snapshot()
            val captureAll = NotificationGate.isCaptureAll()

            // 白名单里可能有已卸载的应用，也一并显示出来，否则用户无从删除
            val known = installed.map { it.packageName }.toSet()
            val missing = selected.filterNot { it in known }.map {
                InstalledApp(it, NotificationGate.appLabel(appContext, it) + "（未安装）")
            }

            _state.value = WhitelistUiState(
                apps = (installed + missing).sortedBy { it.label },
                selected = selected,
                captureAll = captureAll,
                genericRuleEnabled = container.settingsRepo.getBoolean(SettingKeys.GENERIC_RULE_ENABLED, true),
                loaded = true
            )
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun togglePackage(packageName: String) {
        val current = _state.value.selected
        val next = if (packageName in current) current - packageName else current + packageName
        persist(next, _state.value.captureAll)
    }

    /** 手动添加：包名不在已安装列表里（比如双开、轻应用）时用 */
    fun addPackage(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return false
        if (!NotificationGate.packageInstalled(appContext, trimmed)) return false
        persist(_state.value.selected + trimmed, _state.value.captureAll)
        return true
    }

    fun setCaptureAll(value: Boolean) {
        persist(_state.value.selected, value)
    }

    /** 通用兜底规则总开关：关闭后，没有专属规则的应用不再解析 */
    fun setGenericRule(enabled: Boolean) {
        _state.value = _state.value.copy(genericRuleEnabled = enabled)
        viewModelScope.launch {
            container.settingsRepo.put(SettingKeys.GENERIC_RULE_ENABLED, enabled.toString())
        }
    }

    private fun persist(selected: Set<String>, captureAll: Boolean) {
        // 先更新内存快照，监听服务立即生效，不用等数据库写完
        NotificationGate.update(selected, captureAll)
        _state.value = _state.value.copy(selected = selected, captureAll = captureAll)

        viewModelScope.launch {
            container.settingsRepo.put(SettingKeys.WHITELIST, NotificationGate.encode(selected))
            container.settingsRepo.put(SettingKeys.CAPTURE_ALL, captureAll.toString())
        }
    }
}
