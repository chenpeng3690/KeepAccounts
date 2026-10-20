package com.keepaccounts.ui.screen.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.CategoryType
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.notification.InstalledApp
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.KaChips
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.viewmodel.RuleWizardViewModel
import com.keepaccounts.ui.viewmodel.TestResult
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time

private val STEP_TITLES = listOf(
    "选应用", "贴样例", "点选字段", "确认方向", "默认值", "忽略词", "测试验证", "保存"
)

@Composable
fun RuleWizardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = kaViewModel { RuleWizardViewModel(it, context) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showRecentPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题 + 第 X/8 步
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (state.step == 0) onBack() else vm.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = STEP_TITLES[state.step],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "第 ${state.step + 1} / 8 步",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinearProgressIndicator(
            progress = { (state.step + 1) / 8f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // 内容区
        Box(modifier = Modifier.weight(1f)) {
            when (state.step) {
                0 -> StepApp(state.apps, state.packageName, state.appLabel, vm::selectApp)
                1 -> StepSample(state.sampleText, vm::setSample, onPickRecent = { showRecentPicker = true })
                2 -> StepFields(
                    state.sampleText,
                    state.amountCandidates,
                    state.amount,
                    state.merchant,
                    state.anchor,
                    vm::setAmount,
                    vm::setMerchant,
                    vm::setAnchor
                )
                3 -> StepDirection(state.direction, vm::setDirection)
                4 -> StepDefaults(
                    state.accounts,
                    state.accountId,
                    state.categories,
                    state.categoryId,
                    state.direction,
                    vm::setAccount,
                    vm::setCategory
                )
                5 -> StepNoise(
                    state.sampleText,
                    state.recommendedNoise,
                    state.excludeKeywords,
                    vm::refreshNoiseRecommendations,
                    vm::toggleNoise
                )
                6 -> StepTest(
                    state.testCases,
                    state.testResults,
                    vm::setTestCases,
                    vm::runTests,
                    onEnter = {
                        val init = if (state.testCases.isEmpty()) listOf(state.sampleText) else state.testCases
                        vm.setTestCases(init)
                    }
                )
                7 -> StepSave(state, vm::setRuleName)
            }
        }

        // 底栏：上一步 / 下一步 / 保存
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.step > 0) {
                TextButton(onClick = vm::back, modifier = Modifier.weight(1f)) {
                    Text("上一步")
                }
            }
            Surface(
                onClick = {
                    if (state.step == 7) vm.save() else vm.next()
                },
                enabled = if (state.step == 7) true else vm.canNext(),
                modifier = Modifier.weight(if (state.step > 0) 1.4f else 1f),
                shape = RoundedCornerShape(12.dp),
                color = if (state.step == 7 || vm.canNext()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.step == 7) "保存为观察模式" else "下一步",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.step == 7 || vm.canNext()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showRecentPicker) {
        RecentPickerSheet(
            events = state.recentEvents,
            onPick = { e ->
                vm.pickRecent(e)
                showRecentPicker = false
            },
            onDismiss = { showRecentPicker = false }
        )
    }
}

@Composable
private fun StepApp(
    apps: List<InstalledApp>,
    selectedPackage: String?,
    selectedLabel: String,
    onSelect: (String?, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionLabel(text = "这个规则记哪个应用的通知？", modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            item {
                AppRow(
                    label = "通用兜底（所有应用）",
                    sub = "没有专属规则的应用都走它，命中一律进待确认",
                    selected = selectedLabel.isEmpty() && selectedPackage == null,
                    onClick = { onSelect(null, "通用兜底") }
                )
                Spacer(Modifier.height(8.dp))
            }
            items(apps, key = { it.packageName }) { app ->
                AppRow(
                    label = app.label,
                    sub = app.packageName,
                    selected = selectedPackage == app.packageName,
                    onClick = { onSelect(app.packageName, app.label) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppRow(label: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LetterAvatar(text = label.take(1), colorHex = "#3B5BDB", size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StepSample(sample: String, onChange: (String) -> Unit, onPickRecent: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel(text = "把手机上收到的那条通知原文粘贴进来")
        KaCard {
            KaField(
                value = sample,
                onValueChange = onChange,
                label = "通知原文",
                placeholder = "例：微信支付 支付成功 ¥25.00 瑞幸咖啡",
                singleLine = false,
                modifier = Modifier.height(160.dp)
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onPickRecent) { Text("从最近通知里挑一条") }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "可以只贴一条。金额、商户、方向会在下一步自动识别，你只需要确认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepFields(
    sample: String,
    candidates: List<com.keepaccounts.data.parse.AmountExtractor.Hit>,
    amount: Long?,
    merchant: String,
    anchor: String,
    onAmount: (Long?) -> Unit,
    onMerchant: (String) -> Unit,
    onAnchor: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel(text = "原文")
        KaCard {
            Text(
                text = sample,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        SectionLabel(text = "哪段是金额？（点一下确认）")
        if (candidates.isEmpty()) {
            Text(
                text = "没识别到金额，回到上一步换一条含 ¥ 或「元」的样例",
                style = MaterialTheme.typography.bodySmall,
                color = KaTheme.extra.warning
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.forEach { hit ->
                    val selected = hit.cents == amount
                    Surface(
                        onClick = { onAmount(hit.cents) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "¥${Money.format(hit.cents)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        SectionLabel(text = "商户（可改）")
        KaField(value = merchant, onValueChange = onMerchant, label = "商户", placeholder = "瑞幸咖啡")

        SectionLabel(text = "固定文案（识别这条通知的锚点）")
        KaField(value = anchor, onValueChange = onAnchor, label = "锚点词", placeholder = "支付成功")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "规则靠这段固定文案判断「这是不是同一种通知」。改版后这段变了，规则就会失配。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepDirection(direction: String?, onSelect: (String?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel(text = "这笔是支出还是收入？")
        KaCard {
            Text(
                text = "已根据通知文案自动预测，确认即可；预测错了就手动改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            KaChips(
                options = listOf("支出", "收入"),
                selectedIndex = when (direction) {
                    TxType.EXPENSE -> 0
                    TxType.INCOME -> 1
                    else -> -1
                },
                onSelect = { onSelect(if (it == 0) TxType.EXPENSE else TxType.INCOME) }
            )
        }
    }
}

@Composable
private fun StepDefaults(
    accounts: List<com.keepaccounts.data.local.db.AccountEntity>,
    accountId: Long?,
    categories: List<com.keepaccounts.data.local.db.CategoryEntity>,
    categoryId: Long?,
    direction: String?,
    onAccount: (Long?) -> Unit,
    onCategory: (Long?) -> Unit
) {
    val accountNames = accounts.map { it.name }
    val accountIndex = accounts.indexOfFirst { it.id == accountId }.coerceAtLeast(0)
    val type = if (direction == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val filtered = categories.filter { it.type == type }
    val catNames = filtered.map { it.name }
    val catIndex = filtered.indexOfFirst { it.id == categoryId }.coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel(text = "记到哪个账户（解析不出时兜底）")
        KaChips(
            options = accountNames,
            selectedIndex = accountIndex,
            onSelect = { accounts.getOrNull(it)?.id?.let(onAccount) }
        )

        SectionLabel(text = "默认分类")
        KaChips(
            options = catNames,
            selectedIndex = catIndex,
            onSelect = { filtered.getOrNull(it)?.id?.let(onCategory) }
        )
    }
}

@Composable
private fun StepNoise(
    sample: String,
    recommended: List<String>,
    excluded: List<String>,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit
) {
    LaunchedEffect(sample) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel(text = "含这些词的通知不会记账（点一下勾选）")
        if (recommended.isEmpty()) {
            KaCard {
                Text(
                    text = "这条样例里没扫到常见噪音词，可以留空。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recommended.forEach { word ->
                    val on = word in excluded
                    Surface(
                        onClick = { onToggle(word) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (on) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = word,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTest(
    testCases: List<String>,
    results: List<TestResult>,
    onCases: (List<String>) -> Unit,
    onRun: () -> Unit,
    onEnter: () -> Unit
) {
    LaunchedEffect(Unit) { onEnter() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel(text = "用几条样例验证规则（全部 ✅ 才能保存）")
        testCases.forEachIndexed { i, text ->
            KaField(
                value = text,
                onValueChange = { v ->
                    onCases(testCases.toMutableList().also { it[i] = v })
                },
                label = "样例 ${i + 1}",
                singleLine = false
            )
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = { onCases(testCases + "") }) { Text("+ 加一条样例") }
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "测试",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        if (results.isNotEmpty()) {
            SectionLabel(text = "结果")
            results.forEach { r ->
                KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (r.matched) "✅" else "❌",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (r.matched) "匹配" else "不匹配",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (r.matched) KaTheme.extra.income else KaTheme.extra.expense
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = r.input,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (r.matched) {
                        Spacer(Modifier.height(4.dp))
                        val dirLabel = when (r.direction) {
                            TxType.INCOME -> "收入"
                            TxType.EXPENSE -> "支出"
                            else -> "方向不明"
                        }
                        Text(
                            text = "金额 ¥${r.amount?.let { Money.format(it) } ?: "-"} · 方向 $dirLabel · 商户 ${r.merchant ?: "未识别"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (r.amount == null) "原因：未找到金额" else "原因：缺少固定文案片段",
                            style = MaterialTheme.typography.labelSmall,
                            color = KaTheme.extra.expense
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StepSave(
    state: com.keepaccounts.ui.viewmodel.RuleWizardUiState,
    onNameChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel(text = "确认规则")
        KaCard {
            PreviewRow("应用", state.appLabel.ifBlank { "通用兜底" })
            PreviewRow("锚点", state.anchor)
            PreviewRow("方向", when (state.direction) {
                TxType.INCOME -> "收入"
                TxType.EXPENSE -> "支出"
                else -> "-"
            })
            PreviewRow("金额", state.amount?.let { "¥${Money.format(it)}" } ?: "-")
            PreviewRow("商户", state.merchant.ifBlank { "未识别" })
            PreviewRow("忽略词", state.excludeKeywords.joinToString("、").ifBlank { "无" })
        }

        SectionLabel(text = "规则名称")
        KaField(
            value = state.ruleName,
            onValueChange = onNameChange,
            label = "名称",
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        KaCard {
            Text(
                text = "保存后规则处于「观察模式」：命中的通知只记录、不自动记账。你在解析日志里累计 3 条「确认正确」后，它会提示你启用自动记账。",
                style = MaterialTheme.typography.bodySmall,
                color = KaTheme.extra.warning
            )
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RecentPickerSheet(
    events: List<RawEventEntity>,
    onPick: (RawEventEntity) -> Unit,
    onDismiss: () -> Unit
) {
    KaSheet(title = "从最近通知挑一条", onDismiss = onDismiss) {
        if (events.isEmpty()) {
            Text(
                text = "还没有采到通知。先去系统设置里开启通知使用权，再让微信/支付宝推几条。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.take(15).forEach { e ->
                Surface(
                    onClick = { onPick(e) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = (e.appLabel ?: e.packageName) + " · " + Time.formatDateTime(e.receivedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = e.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
