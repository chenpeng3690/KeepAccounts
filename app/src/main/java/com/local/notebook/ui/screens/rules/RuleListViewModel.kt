package com.local.notebook.ui.screens.rules

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.RuleEntity
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.ui.theme.LedgerTone
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RuleRowUi(
    val id: Long,
    val name: String,
    val appLabel: String,
    val appChar: String,
    val tone: LedgerTone,
    val state: String,
    val isBuiltin: Boolean,
    val statsHits7d: Int,
    val statsConfirmed: Int,
    val statsRejected: Int,
    val healthWarning: Boolean,
    val readyToEnable: Boolean
)

data class RuleListUiState(
    val loading: Boolean = true,
    val builtin: List<RuleRowUi> = emptyList(),
    val user: List<RuleRowUi> = emptyList(),
    val fallbackEnabled: Boolean = true
)

@HiltViewModel
class RuleListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<RuleListUiState> = combine(
        repository.rules,
        repository.rawLogs,
        settings.fallbackRuleEnabled,
        labels
    ) { rules, raws, fallback, labelMap ->
        val now = System.currentTimeMillis()
        val cutoff = now - 7 * 24 * 60 * 60 * 1000L

        fun mapRule(rule: RuleEntity): RuleRowUi {
            val appLabel = rule.packageName?.let { pkg ->
                labelMap[pkg] ?: knownLabel(pkg) ?: pkg
            } ?: "通用规则"
            val hits7d = raws.count {
                it.ruleId == rule.id &&
                    it.receivedAt >= cutoff &&
                    it.matchResult != "REJECTED"
            }
            val lastHit = rule.lastHitAt
            val health = rule.statsHits > 0 &&
                (lastHit == null || lastHit < cutoff) &&
                hits7d == 0
            return RuleRowUi(
                id = rule.id,
                name = rule.name,
                appLabel = appLabel,
                appChar = appLabel.take(1),
                tone = toneFor(rule.packageName, appLabel),
                state = rule.state,
                isBuiltin = rule.isBuiltin,
                statsHits7d = hits7d,
                statsConfirmed = rule.statsConfirmed,
                statsRejected = rule.statsRejected,
                healthWarning = health,
                readyToEnable = rule.state == "OBSERVING" &&
                    rule.statsConfirmed >= 3 &&
                    rule.statsRejected == 0
            )
        }

        RuleListUiState(
            loading = false,
            builtin = rules.filter { it.isBuiltin }.map(::mapRule),
            user = rules.filterNot { it.isBuiltin }.map(::mapRule),
            fallbackEnabled = fallback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RuleListUiState()
    )

    init {
        viewModelScope.launch {
            labels.value = withContext(Dispatchers.IO) { loadLabels() }
        }
    }

    fun setBuiltinFallback(enabled: Boolean) {
        viewModelScope.launch { settings.setFallbackRuleEnabled(enabled) }
    }

    fun toggleState(rule: RuleRowUi) {
        val next = if (rule.state == "DISABLED") "ACTIVE" else "DISABLED"
        viewModelScope.launch { repository.setRuleState(rule.id, next) }
    }

    fun enableRule(rule: RuleRowUi) {
        viewModelScope.launch { repository.setRuleState(rule.id, "ACTIVE") }
    }

    fun duplicate(rule: RuleRowUi) {
        viewModelScope.launch { repository.duplicateRule(rule.id) }
    }

    fun move(rule: RuleRowUi, delta: Int) {
        viewModelScope.launch { repository.moveRulePriority(rule.id, delta) }
    }

    fun delete(rule: RuleRowUi) {
        viewModelScope.launch { repository.deleteRule(rule.id) }
    }

    private fun loadLabels(): Map<String, String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        return activities
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .associate { info ->
                info.packageName to runCatching {
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(info.packageName)
            }
    }

    private fun knownLabel(packageName: String): String? = when (packageName) {
        "com.tencent.mm" -> "微信"
        "com.eg.android.AlipayGphone" -> "支付宝"
        else -> null
    }

    private fun toneFor(packageName: String?, label: String): LedgerTone = when {
        packageName == "com.tencent.mm" -> LedgerTone.GREEN
        packageName == "com.eg.android.AlipayGphone" -> LedgerTone.BLUE
        label.contains("抖音") || label.contains("快手") -> LedgerTone.GRAY
        label.contains("拼多多") -> LedgerTone.RED
        label.contains("美团") || label.contains("饿了么") -> LedgerTone.ORANGE
        else -> LedgerTone.entries[(packageName ?: label).hashCode().mod(LedgerTone.entries.size)]
    }
}
