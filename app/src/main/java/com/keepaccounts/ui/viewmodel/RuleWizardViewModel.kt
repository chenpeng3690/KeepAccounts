package com.keepaccounts.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.data.local.db.RuleEntity
import com.keepaccounts.data.local.db.RuleState
import com.keepaccounts.data.parse.AmountExtractor
import com.keepaccounts.data.parse.DirectionClassifier
import com.keepaccounts.data.parse.MerchantClassifier
import com.keepaccounts.data.parse.NoiseFilter
import com.keepaccounts.data.parse.RuleTemplate
import com.keepaccounts.notification.InstalledApp
import com.keepaccounts.notification.loadLaunchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TestResult(
    val input: String,
    val matched: Boolean,
    val amount: Long?,
    val direction: String?,
    val merchant: String?
)

data class RuleWizardUiState(
    val step: Int = 0,
    val apps: List<InstalledApp> = emptyList(),
    val appSelected: Boolean = false,
    val packageName: String? = null, // null = 通用兜底
    val appLabel: String = "",
    val sampleText: String = "",
    val recentEvents: List<RawEventEntity> = emptyList(),
    val amount: Long? = null,
    val amountCandidates: List<AmountExtractor.Hit> = emptyList(),
    val merchant: String = "",
    val anchor: String = "",
    val direction: String? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val accountId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val categoryId: Long? = null,
    val recommendedNoise: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val testCases: List<String> = emptyList(),
    val testResults: List<TestResult> = emptyList(),
    val ruleName: String = "",
    val saved: Boolean = false
)

class RuleWizardViewModel(
    private val container: AppContainer,
    context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(RuleWizardUiState())
    val state: StateFlow<RuleWizardUiState> = _state.asStateFlow()

    private val ruleDao = container.db.ruleDao()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = loadLaunchableApps(context)
            val accounts = container.db.accountDao().listActive()
            val categories = container.categoryRepo.listAll()
            val recent = container.db.rawEventDao().observeRecent(200).firstOrNull() ?: emptyList()
            _state.update {
                it.copy(
                    apps = apps,
                    accounts = accounts,
                    categories = categories,
                    recentEvents = recent
                )
            }
        }
    }

    fun selectApp(packageName: String?, label: String) {
        _state.update {
            it.copy(
                appSelected = true,
                packageName = packageName,
                appLabel = label
            )
        }
    }

    fun setSample(text: String) {
        // 每次改样例都重新识别金额/商户/方向/锚定词，让后续步骤即时刷新
        val hits = AmountExtractor.hits(text)
        val amount = AmountExtractor.extract(text)
        val merchant = MerchantClassifier.extract(null, text) ?: ""
        val anchor = findAnchor(text)
        val direction = DirectionClassifier.classify(text)
        _state.update {
            it.copy(
                sampleText = text,
                amountCandidates = hits,
                amount = amount,
                merchant = merchant,
                anchor = anchor,
                direction = direction,
                ruleName = if (it.ruleName.isBlank()) defaultName(it.appLabel, anchor) else it.ruleName
            )
        }
    }

    fun pickRecent(event: RawEventEntity) {
        setSample(event.text)
    }

    fun setAmount(cents: Long?) {
        _state.update { it.copy(amount = cents) }
    }

    fun setMerchant(text: String) {
        _state.update { it.copy(merchant = text) }
    }

    fun setAnchor(text: String) {
        _state.update { it.copy(anchor = text, ruleName = if (it.ruleName.isBlank()) defaultName(it.appLabel, text) else it.ruleName) }
    }

    fun setDirection(dir: String?) {
        _state.update { it.copy(direction = dir) }
    }

    fun setAccount(id: Long?) {
        _state.update { it.copy(accountId = id) }
    }

    fun setCategory(id: Long?) {
        _state.update { it.copy(categoryId = id) }
    }

    fun toggleNoise(word: String) {
        _state.update {
            val cur = it.excludeKeywords
            it.copy(
                excludeKeywords = if (word in cur) cur - word else cur + word
            )
        }
    }

    fun refreshNoiseRecommendations() {
        val text = _state.value.sampleText
        val recommended = NoiseFilter.GLOBAL_NOISE.filter { text.contains(it) }
        _state.update { it.copy(recommendedNoise = recommended) }
    }

    fun setRuleName(name: String) {
        _state.update { it.copy(ruleName = name) }
    }

    fun setTestCases(cases: List<String>) {
        _state.update { it.copy(testCases = cases, testResults = emptyList()) }
    }

    fun runTests() {
        val s = _state.value
        val anchor = s.anchor.trim()
        val results = s.testCases.map { input ->
            val template = RuleTemplate.fromJson(templateJson(anchor))
            val amount = AmountExtractor.extract(input)
            val matched = anchor.isNotEmpty() && template.matches(input) && amount != null
            TestResult(
                input = input,
                matched = matched,
                amount = amount,
                direction = DirectionClassifier.classify(input),
                merchant = MerchantClassifier.extract(null, input)
            )
        }
        _state.update { it.copy(testResults = results) }
    }

    fun save() {
        val s = _state.value
        if (s.saved) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ruleDao.insert(
                RuleEntity(
                    name = s.ruleName.ifBlank { defaultName(s.appLabel, s.anchor) },
                    packageName = s.packageName,
                    priority = 50,
                    builtin = false,
                    state = RuleState.OBSERVING,
                    templateJson = templateJson(s.anchor.trim()),
                    excludeKeywords = s.excludeKeywords.takeIf { it.isNotEmpty() }?.joinToString(","),
                    defaultAccountId = s.accountId,
                    defaultCategoryId = s.categoryId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun next() {
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(7)) }
    }

    fun back() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    /** 当前步能否进入下一步 */
    fun canNext(): Boolean {
        val s = _state.value
        return when (s.step) {
            0 -> s.appSelected
            1 -> s.sampleText.isNotBlank()
            2 -> s.amount != null && s.anchor.isNotBlank()
            3 -> s.direction != null
            4 -> s.accountId != null
            5 -> true
            6 -> s.testResults.isNotEmpty() && s.testResults.all { it.matched }
            else -> true
        }
    }

    private fun findAnchor(text: String): String {
        for (kw in listOf(
            "支付成功", "付款成功", "已支付", "收款成功", "到账", "退款成功",
            "收款", "退款", "收益", "扣款", "消费", "入账", "转账", "已收款"
        )) {
            if (text.contains(kw)) return kw
        }
        return ""
    }

    private fun defaultName(label: String, anchor: String): String =
        when {
            label.isNotBlank() && anchor.isNotBlank() -> "$label · $anchor"
            label.isNotBlank() -> label
            anchor.isNotBlank() -> anchor
            else -> "自定义规则"
        }

    private fun templateJson(literal: String): String {
        if (literal.isBlank()) return "[]"
        val escaped = literal.replace("\\", "\\\\").replace("\"", "\\\"")
        return """[{"t":"literal","v":"$escaped"}]"""
    }
}
