package com.local.notebook.ui.screens.rulewizard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.RuleEntity
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.domain.parser.AmountParser
import com.local.notebook.domain.parser.Direction
import com.local.notebook.domain.parser.DirectionParser
import com.local.notebook.domain.parser.MerchantExtractor
import com.local.notebook.domain.parser.NoiseFilter
import com.local.notebook.domain.parser.NotificationRuleEngine
import com.local.notebook.domain.parser.ParseOutcome
import com.local.notebook.domain.parser.ParsedAmount
import com.local.notebook.domain.parser.RuleDefinition
import com.local.notebook.domain.parser.RuleTemplateCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class WizardApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?
)

data class WizardTestResult(
    val text: String,
    val matched: Boolean,
    val amountCents: Long?,
    val merchant: String?,
    val direction: String?,
    val reason: String?,
    val noiseFailure: Boolean
)

data class RuleWizardUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val step: Int = 0,
    val installedApps: List<WizardApp> = emptyList(),
    val appQuery: String = "",
    val selectedPackage: String? = null,
    val selectedAppLabel: String = "",
    val sample: String = "",
    val amountInput: String = "",
    val amountCents: Long? = null,
    val amountRaw: String? = null,
    val amountCandidates: List<ParsedAmount> = emptyList(),
    val merchant: String? = null,
    val merchantCandidates: List<String> = emptyList(),
    val direction: String = "EXPENSE",
    val directionAutoDetected: Boolean = true,
    val directionEdited: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val ignoreWords: Set<String> = emptySet(),
    val ignoreSuggestions: List<String> = emptyList(),
    val customIgnoreWord: String = "",
    val testInputs: List<String> = emptyList(),
    val testResults: List<WizardTestResult> = emptyList(),
    val testsRun: Boolean = false,
    val recentRaw: List<NotificationRawEntity> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null
) {
    val nextEnabled: Boolean
        get() = when (step) {
            0 -> selectedPackage != null
            1 -> sample.isNotBlank()
            2 -> (amountCents ?: 0) > 0
            3 -> true
            4 -> accountId != null && categoryId != null
            5 -> true
            6 -> testsRun && testResults.size >= 2 &&
                testResults[0].matched && testResults[1].matched
            else -> true
        }
}

@HiltViewModel
class RuleWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ruleId: Long = savedStateHandle.get<Long>("ruleId") ?: -1L
    private var existingRule: RuleEntity? = null
    private val engine = NotificationRuleEngine()

    private val _state = MutableStateFlow(RuleWizardUiState())
    val state: StateFlow<RuleWizardUiState> = _state

    init {
        viewModelScope.launch {
            val accounts = repository.accounts.first()
            val categories = repository.categories.first()
            val apps = withContext(Dispatchers.IO) { loadApps() }
            val expenseCategory = categories.firstOrNull { it.type == "EXPENSE" }
            _state.update {
                it.copy(
                    loading = false,
                    installedApps = apps,
                    accounts = accounts,
                    categories = categories,
                    accountId = accounts.firstOrNull()?.id,
                    categoryId = expenseCategory?.id
                )
            }
            if (ruleId > 0) {
                loadExistingRule()
            }
        }
    }

    fun setStep(step: Int) {
        _state.update { it.copy(step = step.coerceIn(0, 7), error = null) }
    }

    fun next() {
        val current = _state.value
        if (!current.nextEnabled) {
            _state.update { it.copy(error = validationMessage(current)) }
            return
        }
        if (current.step == 6 && !current.nextEnabled) return
        when (current.step) {
            0 -> loadRecentNotifications()
            2 -> detectFromSample()
            4 -> prepareIgnoreWords()
            5 -> prepareTests()
        }
        _state.update { it.copy(step = (current.step + 1).coerceAtMost(7), error = null) }
    }

    fun previous() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }
    }

    fun setAppQuery(value: String) {
        _state.update { it.copy(appQuery = value) }
    }

    fun selectApp(app: WizardApp) {
        _state.update {
            it.copy(
                selectedPackage = app.packageName,
                selectedAppLabel = app.label,
                error = null
            )
        }
    }

    fun setSample(value: String) {
        _state.update { it.copy(sample = value) }
        resetTests()
        detectFromSample()
    }

    fun setSampleFromRaw(raw: NotificationRawEntity) {
        val text = listOfNotNull(
            raw.title?.takeIf { it.isNotBlank() },
            raw.text?.takeIf { it.isNotBlank() }
        ).joinToString("\n")
        setSample(text)
    }

    fun setAmountInput(value: String) {
        val cents = value.trim()
            .toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.movePointRight(2)
            ?.toLong()
            ?.takeIf { it > 0 }
        _state.update { it.copy(amountInput = value, amountCents = cents, amountRaw = null) }
        resetTests()
    }

    fun selectAmountCandidate(candidate: ParsedAmount) {
        _state.update {
            it.copy(
                amountCents = candidate.cents,
                amountRaw = candidate.raw,
                amountInput = BigDecimal.valueOf(candidate.cents)
                    .movePointLeft(2)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
            )
        }
        resetTests()
    }

    fun setMerchant(value: String) {
        _state.update { it.copy(merchant = value.trim().takeIf { v -> v.isNotEmpty() }) }
        resetTests()
    }

    fun setDirection(direction: String) {
        _state.update { current ->
            val categories = current.categories.filter { it.type == direction }
            val keep = categories.any { it.id == current.categoryId }
            current.copy(
                direction = direction,
                directionAutoDetected = false,
                directionEdited = true,
                categoryId = if (keep) current.categoryId else categories.firstOrNull()?.id
            )
        }
        resetTests()
    }

    fun selectAccount(id: Long) {
        _state.update { it.copy(accountId = id) }
    }

    fun selectCategory(id: Long) {
        _state.update { it.copy(categoryId = id) }
    }

    fun toggleIgnoreWord(word: String) {
        _state.update {
            val selected = it.ignoreWords.toMutableSet()
            if (!selected.add(word)) selected.remove(word)
            it.copy(ignoreWords = selected)
        }
        resetTests()
    }

    fun setCustomIgnoreWord(value: String) {
        _state.update { it.copy(customIgnoreWord = value) }
    }

    fun addCustomIgnoreWord() {
        val word = _state.value.customIgnoreWord.trim()
        if (word.isEmpty()) return
        _state.update {
            it.copy(
                ignoreSuggestions = (it.ignoreSuggestions + word).distinct(),
                ignoreWords = it.ignoreWords + word,
                customIgnoreWord = ""
            )
        }
        resetTests()
    }

    fun setTestInput(index: Int, value: String) {
        _state.update {
            val inputs = it.testInputs.toMutableList()
            if (index in inputs.indices) inputs[index] = value
            it.copy(testInputs = inputs, testResults = emptyList(), testsRun = false, error = null)
        }
    }

    fun runTests() {
        val current = _state.value
        if (current.testInputs.isEmpty()) return
        val draft = buildDraftRule(current)
        val results = current.testInputs.map { text ->
            val payload = com.local.notebook.domain.parser.NotificationPayload(
                packageName = current.selectedPackage.orEmpty(),
                appLabel = current.selectedAppLabel,
                title = "",
                text = text,
                notificationKey = "test",
                groupKey = null,
                category = null,
                postedAt = System.currentTimeMillis(),
                receivedAt = System.currentTimeMillis()
            )
            when (val outcome = engine.parse(payload, listOf(draft), fallbackEnabled = false)) {
                is ParseOutcome.Matched -> WizardTestResult(
                    text = text,
                    matched = true,
                    amountCents = outcome.amount,
                    merchant = outcome.merchant,
                    direction = outcome.direction.name,
                    reason = null,
                    noiseFailure = false
                )
                is ParseOutcome.Pending -> WizardTestResult(
                    text = text,
                    matched = false,
                    amountCents = outcome.amount,
                    merchant = outcome.merchant,
                    direction = outcome.direction?.name,
                    reason = outcome.note,
                    noiseFailure = false
                )
                is ParseOutcome.Rejected -> WizardTestResult(
                    text = text,
                    matched = false,
                    amountCents = null,
                    merchant = null,
                    direction = null,
                    reason = "${outcome.layer} ${outcome.reason}",
                    noiseFailure = outcome.layer == "L1" || outcome.reason.contains("噪音")
                )
                ParseOutcome.Ignored -> WizardTestResult(
                    text = text,
                    matched = false,
                    amountCents = null,
                    merchant = null,
                    direction = null,
                    reason = "未匹配规则",
                    noiseFailure = false
                )
            }
        }
        val error = if (results.size >= 2 && results.take(2).any { !it.matched }) {
            "前两条测试用例必须匹配成功才能保存"
        } else {
            null
        }
        _state.update {
            it.copy(testResults = results, testsRun = true, error = error)
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving || !current.nextEnabled) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = existingRule
            val rule = RuleEntity(
                id = existing?.id ?: 0,
                name = existing?.name
                    ?: current.selectedAppLabel.ifBlank { current.selectedPackage ?: "自定义规则" },
                packageName = current.selectedPackage,
                priority = existing?.priority ?: 10,
                isBuiltin = existing?.isBuiltin ?: false,
                state = existing?.state ?: "OBSERVING",
                templateJson = buildTemplate(current),
                excludeKeywords = current.ignoreWords.joinToString(","),
                defaultAccountId = current.accountId,
                defaultCategoryId = current.categoryId,
                amountHint = current.amountCents,
                statsHits = existing?.statsHits ?: 0,
                statsConfirmed = existing?.statsConfirmed ?: 0,
                statsRejected = existing?.statsRejected ?: 0,
                lastHitAt = existing?.lastHitAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            repository.saveRule(rule)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun loadRecentNotifications() {
        val pkg = _state.value.selectedPackage ?: return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                repository.recentRawForPackage(
                    pkg,
                    System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                )
            }
            _state.update { it.copy(recentRaw = list) }
        }
    }

    private fun detectFromSample() {
        val current = _state.value
        val text = current.sample
        if (text.isBlank()) return
        val amount = AmountParser.bestAmount(text)
        val merchant = MerchantExtractor.extract(text)
        val detectedDirection = DirectionParser.detect(text)
        _state.update {
            it.copy(
                amountCents = amount?.cents,
                amountRaw = amount?.raw,
                amountInput = amount?.let { parsed ->
                    BigDecimal.valueOf(parsed.cents)
                        .movePointLeft(2)
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString()
                }.orEmpty(),
                amountCandidates = AmountParser.findAllAmounts(text),
                merchant = merchant,
                merchantCandidates = MerchantExtractor.candidates(text),
                direction = if (!it.directionEdited && detectedDirection != Direction.UNKNOWN) {
                    detectedDirection.name
                } else {
                    it.direction
                },
                directionAutoDetected = !it.directionEdited && detectedDirection != Direction.UNKNOWN
            )
        }
    }

    private fun prepareIgnoreWords() {
        val current = _state.value
        val defaults = listOf("限时", "满减", "优惠券", "活动", "红包", "会员")
        val found = NoiseFilter.builtinNoiseWords.filter { current.sample.contains(it) }
        val suggestions = (found + defaults).distinct().take(8)
        _state.update {
            it.copy(
                ignoreSuggestions = suggestions,
                ignoreWords = if (it.isEdit) it.ignoreWords else suggestions.toSet()
            )
        }
    }

    private fun prepareTests() {
        val current = _state.value
        if (current.testInputs.isNotEmpty()) {
            _state.update { it.copy(testResults = emptyList(), testsRun = false) }
            return
        }
        val sample = current.sample.ifBlank { "支付成功 ¥25.00 测试商户" }
        val amountRaw = current.amountRaw
        val second = if (!amountRaw.isNullOrBlank() && sample.contains(amountRaw)) {
            sample.replace(amountRaw, "18.50元")
        } else {
            "$sample\n18.50元"
        }
        val noise = "限时活动！满100减20，速来抢购"
        _state.update {
            it.copy(
                testInputs = listOf(sample, second, noise),
                testResults = emptyList(),
                testsRun = false
            )
        }
    }

    private fun loadExistingRule() {
        viewModelScope.launch {
            val rule = repository.getRule(ruleId) ?: return@launch
            existingRule = rule
            val current = _state.value
            val appLabel = current.installedApps
                .firstOrNull { it.packageName == rule.packageName }?.label
                ?: knownLabel(rule.packageName)
                ?: rule.packageName.orEmpty()
            val decoded = RuleTemplateCodec.decode(rule.templateJson)
            val literals = decoded.literalGroups.flatten()
            val recent = rule.packageName?.let {
                repository.recentRawForPackage(
                    it,
                    System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                )
            }.orEmpty()
            val sample = recent.firstOrNull { raw ->
                val text = listOfNotNull(raw.title, raw.text).joinToString("\n")
                AmountParser.bestAmount(text) != null
            }?.let { raw ->
                listOfNotNull(raw.title?.takeIf { it.isNotBlank() }, raw.text?.takeIf { it.isNotBlank() })
                    .joinToString("\n")
            } ?: (literals.joinToString(" ") + " 25.00元")
            val detected = DirectionParser.detect(sample)
            val direction = decoded.direction?.name
                ?: detected.takeIf { it != Direction.UNKNOWN }?.name
                ?: "EXPENSE"
            _state.update {
                it.copy(
                    isEdit = true,
                    selectedPackage = rule.packageName,
                    selectedAppLabel = appLabel,
                    sample = sample,
                    direction = direction,
                    directionAutoDetected = decoded.direction == null,
                    directionEdited = decoded.direction != null,
                    accountId = rule.defaultAccountId ?: it.accountId,
                    categoryId = rule.defaultCategoryId ?: it.categoryId,
                    ignoreWords = rule.excludeKeywords
                        .split(',', '，')
                        .map { word -> word.trim() }
                        .filter { word -> word.isNotEmpty() }
                        .toSet()
                )
            }
            detectFromSample()
        }
    }

    private fun validationMessage(state: RuleWizardUiState): String = when (state.step) {
        0 -> "请选择一个应用"
        1 -> "请先粘贴一条通知原文"
        2 -> "请设置一个有效金额"
        4 -> "请选择默认账户和分类"
        6 -> "请先测试，并确保前两条测试用例匹配成功"
        else -> "请完善必填信息"
    }

    private fun resetTests() {
        _state.update {
            it.copy(testResults = emptyList(), testsRun = false, error = null)
        }
    }

    private fun buildTemplate(state: RuleWizardUiState): String {
        var cleaned = state.sample
        state.amountRaw?.takeIf { it.isNotBlank() }?.let { cleaned = cleaned.replace(it, " ") }
        MerchantExtractor.candidates(state.sample).forEach { candidate ->
            cleaned = cleaned.replace(candidate, " ")
        }
        state.merchant?.takeIf { it.isNotBlank() }?.let { cleaned = cleaned.replace(it, " ") }
        cleaned = Regex("""[¥￥]\s*[0-9,.]+""").replace(cleaned, " ")
        cleaned = Regex("""[0-9,.]+\s*(?:元|块)""").replace(cleaned, " ")

        val chunks = cleaned
            .split(Regex("[\n，,。；;！!？?：:]"))
            .map { it.trim().trim('。', '，', ',', '.', '-', '—', '|', '·', ' ') }
            .filter { it.length in 2..20 }
            .distinct()
            .take(3)

        val literals = if (chunks.isEmpty()) {
            listOf(state.selectedAppLabel.ifBlank { state.selectedPackage ?: "规则" })
        } else {
            chunks
        }
        return literals.joinToString("|") { "literal:$it" } +
            "|slot:amount|slot:merchant|fuzzy:true|direction:${state.direction}"
    }

    private fun buildDraftRule(state: RuleWizardUiState): RuleDefinition {
        val decoded = RuleTemplateCodec.decode(buildTemplate(state))
        return RuleDefinition(
            id = -1,
            name = "测试规则",
            packageName = state.selectedPackage,
            priority = 10,
            state = "ACTIVE",
            literalGroups = decoded.literalGroups,
            fuzzy = decoded.fuzzy,
            explicitDirection = decoded.direction,
            excludeKeywords = state.ignoreWords.toList(),
            defaultAccountId = state.accountId,
            defaultCategoryId = state.categoryId
        )
    }

    private fun loadApps(): List<WizardApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        return activities
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                val label = runCatching {
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(info.packageName)
                val icon = runCatching {
                    context.packageManager.getApplicationIcon(info).toBitmap(96, 96)
                }.getOrNull()
                WizardApp(info.packageName, label, icon)
            }
            .sortedBy { it.label }
    }

    private fun knownLabel(packageName: String?): String? = when (packageName) {
        "com.tencent.mm" -> "微信"
        "com.eg.android.AlipayGphone" -> "支付宝"
        else -> null
    }
}
