package com.local.notebook.ui.screens.rulewizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.ui.components.AccountPickerSheet
import com.local.notebook.ui.components.CategoryPickerSheet
import com.local.notebook.ui.components.FormRow
import com.local.notebook.ui.components.FormTextFieldRow
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.TypeToggle
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.formatMoney

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleWizardScreen(
    onClose: () -> Unit,
    viewModel: RuleWizardViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRecentSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(
                icon = R.drawable.ic_close,
                contentDescription = "关闭",
                onClick = onClose
            )
            Text(
                if (state.isEdit) "编辑规则" else "新建规则",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                "${state.step + 1} / 8",
                fontSize = 11.sp,
                color = p.ink3,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(8) { index ->
                val color = when {
                    index < state.step -> p.brand
                    index == state.step -> p.brand.copy(alpha = 0.45f)
                    else -> p.line
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (state.step) {
                0 -> StepAppSelection(
                    state = state,
                    onQueryChange = viewModel::setAppQuery,
                    onSelect = viewModel::selectApp
                )
                1 -> StepSample(
                    state = state,
                    onSampleChange = viewModel::setSample,
                    onPickRecent = {
                        viewModel.loadRecentNotifications()
                        showRecentSheet = true
                    }
                )
                2 -> StepFields(
                    state = state,
                    onAmountInput = viewModel::setAmountInput,
                    onAmountCandidate = viewModel::selectAmountCandidate,
                    onMerchantInput = viewModel::setMerchant
                )
                3 -> StepDirection(state = state, onDirection = viewModel::setDirection)
                4 -> StepDefaults(
                    state = state,
                    onAccount = { showAccountSheet = true },
                    onCategory = { showCategorySheet = true }
                )
                5 -> StepIgnoreWords(state = state, viewModel = viewModel)
                6 -> StepTest(state = state, onRunTests = viewModel::runTests, onTestInput = viewModel::setTestInput)
                else -> StepSummary(state = state)
            }
        }

        state.error?.let { error ->
            Text(
                error,
                fontSize = 10.5.sp,
                color = p.expense,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (state.step > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.card)
                        .clickable(onClick = viewModel::previous),
                    contentAlignment = Alignment.Center
                ) {
                    Text("上一步", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                }
            }
            val canProceed = state.nextEnabled && !state.saving
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canProceed) p.brand else p.brand.copy(alpha = 0.45f))
                    .clickable(enabled = canProceed) {
                        if (state.step == 7) viewModel.save() else viewModel.next()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        state.step == 7 && state.saving -> "保存中…"
                        state.step == 7 -> if (state.isEdit) "保存修改" else "保存并开始观察"
                        state.step == 6 -> "下一步"
                        else -> "下一步"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }

    if (showRecentSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRecentSheet = false },
            sheetState = sheetState,
            containerColor = p.card
        ) {
            Text(
                "最近 7 天的通知",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (state.recentRaw.isEmpty()) {
                    Text(
                        "这个应用最近 7 天没有留下通知。可以先手动粘贴一条。",
                        fontSize = 11.5.sp,
                        color = p.ink3,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    state.recentRaw.take(12).forEach { raw ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.cardAlt)
                                .clickable {
                                    viewModel.setSampleFromRaw(raw)
                                    showRecentSheet = false
                                }
                                .padding(10.dp)
                        ) {
                            Text(
                                raw.title.orEmpty().ifBlank { raw.appLabel ?: raw.packageName },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = p.ink,
                                maxLines = 1
                            )
                            Text(
                                raw.text.orEmpty().replace("\n", " "),
                                fontSize = 10.5.sp,
                                color = p.ink3,
                                modifier = Modifier.padding(top = 3.dp),
                                maxLines = 2
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showCategorySheet) {
        CategoryPickerSheet(
            categories = state.categories.filter { it.type == state.direction },
            selectedId = state.categoryId,
            onSelect = viewModel::selectCategory,
            onDismiss = { showCategorySheet = false }
        )
    }
    if (showAccountSheet) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.accountId,
            onSelect = viewModel::selectAccount,
            onDismiss = { showAccountSheet = false }
        )
    }
}

@Composable
private fun StepAppSelection(
    state: RuleWizardUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (WizardApp) -> Unit
) {
    val p = LedgerTheme.palette
    val keyword = state.appQuery.trim()
    val apps = state.installedApps.filter {
        keyword.isEmpty() ||
            it.label.contains(keyword, ignoreCase = true) ||
            it.packageName.contains(keyword, ignoreCase = true)
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "先选择应用",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink
            )
            Text(
                "选一条来自哪个应用的通知。通用兜底规则在规则列表里单独开关。",
                fontSize = 10.5.sp,
                color = p.ink3,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(p.card)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = p.ink3,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = state.appQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.5.sp, color = p.ink),
                    cursorBrush = SolidColor(p.brand),
                    decorationBox = { inner ->
                        Box {
                            if (state.appQuery.isEmpty()) {
                                Text("搜索应用", fontSize = 12.5.sp, color = p.ink3)
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        items(apps, key = { it.packageName }) { app ->
            val selected = app.packageName == state.selectedPackage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) p.brandSoft else Color.Transparent)
                    .clickable { onSelect(app) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.cardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(app.label.take(1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = p.ink)
                    Text(app.packageName, fontSize = 10.sp, color = p.ink3, maxLines = 1)
                }
                if (selected) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "已选择",
                        tint = p.brand,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StepSample(
    state: RuleWizardUiState,
    onSampleChange: (String) -> Unit,
    onPickRecent: () -> Unit
) {
    val p = LedgerTheme.palette
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("把通知原文粘贴进来")
        Text(
            "复制手机上收到的那条通知，原样粘贴即可。先贴一条就能开始。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
        )
        LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(14.dp)) {
            BasicTextField(
                value = state.sample,
                onValueChange = onSampleChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = p.ink,
                    lineHeight = 21.sp
                ),
                cursorBrush = SolidColor(p.brand),
                decorationBox = { inner ->
                    Box {
                        if (state.sample.isEmpty()) {
                            Text(
                                "例如：\n微信支付\n支付成功\n¥25.00\n瑞幸咖啡(国贸店)",
                                fontSize = 13.sp,
                                color = p.ink3,
                                lineHeight = 21.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(p.brandSoft)
                .clickable(onClick = onPickRecent)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_clock),
                contentDescription = null,
                tint = p.brandSoftText,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "从最近通知里挑一条",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.brandSoftText,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${state.recentRaw.size} 条",
                fontSize = 10.5.sp,
                color = p.brandSoftText.copy(alpha = 0.75f)
            )
        }
        Text(
            "粘贴的原文只用于生成规则和测试，不会上传。",
            fontSize = 10.sp,
            color = p.ink3,
            modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepFields(
    state: RuleWizardUiState,
    onAmountInput: (String) -> Unit,
    onAmountCandidate: (com.local.notebook.domain.parser.ParsedAmount) -> Unit,
    onMerchantInput: (String) -> Unit
) {
    val p = LedgerTheme.palette
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("这笔钱是多少？花给谁了？")
        Text(
            "点一下下面通知里的数字和名字就行，不用写任何规则。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
        )
        LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(14.dp)) {
            Text(
                state.sample.ifBlank { "（还没有粘贴样例）" },
                fontSize = 13.sp,
                color = p.ink,
                lineHeight = 21.sp
            )
        }

        if (state.amountCandidates.isNotEmpty()) {
            SectionLabel("点一下数字设为金额")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                state.amountCandidates.forEach { candidate ->
                    val selected = candidate.cents == state.amountCents
                    Chip(
                        text = "¥${formatMoney(candidate.cents)}",
                        selected = selected,
                        onClick = { onAmountCandidate(candidate) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        FormTextFieldRow(
            label = "金额",
            value = state.amountInput,
            onValueChange = onAmountInput,
            placeholder = "0.00",
            keyboardType = KeyboardType.Decimal
        )

        if (state.merchantCandidates.isNotEmpty()) {
            SectionLabel("点一下商户名")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                state.merchantCandidates.forEach { candidate ->
                    Chip(
                        text = candidate,
                        selected = candidate == state.merchant,
                        onClick = { onMerchantInput(candidate) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        FormTextFieldRow(
            label = "商户",
            value = state.merchant.orEmpty(),
            onValueChange = onMerchantInput,
            placeholder = "识别不到可留空"
        )
        Text(
            "时间默认使用通知的到达时间。",
            fontSize = 10.sp,
            color = p.ink3,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepDirection(
    state: RuleWizardUiState,
    onDirection: (String) -> Unit
) {
    val p = LedgerTheme.palette
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("这笔钱是支出还是收入？")
        Text(
            "系统会根据通知里的词自动判断，你可以改。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
        )
        TypeToggle(type = state.direction, onTypeChange = onDirection)
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.directionAutoDetected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(p.successSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("已自动判断", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = p.successText)
                }
            } else {
                Text("手动选择", fontSize = 10.sp, color = p.ink3)
            }
        }
        Text(
            "同时出现「退款」和「支付」时，会优先按收入处理。",
            fontSize = 10.sp,
            color = p.ink3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepDefaults(
    state: RuleWizardUiState,
    onAccount: () -> Unit,
    onCategory: () -> Unit
) {
    val p = LedgerTheme.palette
    val accountName = state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "请选择"
    val categoryName = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "请选择"
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("设置默认账户和分类")
        Text(
            "通知里解析不出的时候，会用这里的默认值兜底。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
        )
        FormRow(label = "默认账户", value = accountName, onClick = onAccount)
        Spacer(Modifier.height(8.dp))
        FormRow(label = "默认分类", value = categoryName, onClick = onCategory)
        Text(
            "之后在待确认箱里也可以单独修改每一条。",
            fontSize = 10.sp,
            color = p.ink3,
            modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepIgnoreWords(
    state: RuleWizardUiState,
    viewModel: RuleWizardViewModel
) {
    val p = LedgerTheme.palette
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("哪些通知不该记账？")
        Text(
            "含这些词的通知不会记账。系统已按这条样例推荐了几个，默认全部勾选。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            state.ignoreSuggestions.forEach { word ->
                Chip(
                    text = word,
                    selected = word in state.ignoreWords,
                    onClick = { viewModel.toggleIgnoreWord(word) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                FormTextFieldRow(
                    label = "自定义",
                    value = state.customIgnoreWord,
                    onValueChange = viewModel::setCustomIgnoreWord,
                    placeholder = "再加一个词"
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(p.brandSoft)
                    .clickable { viewModel.addCustomIgnoreWord() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("添加", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.brandSoftText)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTest(
    state: RuleWizardUiState,
    onRunTests: () -> Unit,
    onTestInput: (Int, String) -> Unit
) {
    val p = LedgerTheme.palette
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("试一下，看准不准")
        Text(
            "用几条最近收到的通知试跑，全部通过才能保存。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
        )
        state.testInputs.forEachIndexed { index, text ->
            LedgerCard(
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(11.dp)
            ) {
                Text(
                    if (index == 2) "噪音用例" else "用例 ${index + 1}",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.ink3,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                BasicTextField(
                    value = text,
                    onValueChange = { onTestInput(index, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    textStyle = TextStyle(fontSize = 11.sp, color = p.ink, lineHeight = 17.sp),
                    cursorBrush = SolidColor(p.brand)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.brandSoft)
                .clickable(onClick = onRunTests),
            contentAlignment = Alignment.Center
        ) {
            Text("测试这几条", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = p.brandSoftText)
        }

        if (state.testResults.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            state.testResults.forEach { result ->
                TestResultCard(result)
                Spacer(Modifier.height(9.dp))
            }
            val noiseResult = state.testResults.getOrNull(2)
            if (noiseResult != null && !noiseResult.matched && noiseResult.noiseFailure) {
                LedgerCard(
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(11.dp)
                ) {
                    Text(
                        "这条不匹配是正常的",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.warnText
                    )
                    Text(
                        "系统已把它判定为噪音通知，会和其他活动推送一起自动过滤，不影响规则生效。",
                        fontSize = 10.5.sp,
                        color = p.warnText.copy(alpha = 0.9f),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (state.ignoreWords.isNotEmpty()) {
            SectionLabel("自动加入的过滤词")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.ignoreWords.forEach { word ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.card)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(word, fontSize = 10.5.sp, color = p.ink2)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepSummary(state: RuleWizardUiState) {
    val p = LedgerTheme.palette
    val accountName = state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "未选择"
    val categoryName = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "未选择"
    val directionText = if (state.direction == "INCOME") "收入" else "支出"
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("确认一下")
        Text(
            if (state.isEdit) "保存后会按当前状态继续生效。" else
                "新规则先进入观察模式：命中的通知会进入待确认，确认 3 条后可启用自动记账。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
        )
        LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(14.dp)) {
            SummaryRow("应用", state.selectedAppLabel.ifBlank { state.selectedPackage.orEmpty() })
            SummaryRow("金额", state.amountCents?.let { "¥${formatMoney(it)}" } ?: "未设置")
            SummaryRow("商户", state.merchant ?: "未识别")
            SummaryRow("方向", directionText)
            SummaryRow("默认账户", accountName)
            SummaryRow("默认分类", categoryName)
            SummaryRow("过滤词", state.ignoreWords.joinToString("、").ifBlank { "无" })
            SummaryRow(
                "测试结果",
                "${state.testResults.count { it.matched }} / ${state.testResults.size} 通过"
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TestResultCard(result: WizardTestResult) {
    val p = LedgerTheme.palette
    LedgerCard(shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(11.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(if (result.matched) p.successSoft else p.dangerSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (result.matched) R.drawable.ic_check else R.drawable.ic_close
                    ),
                    contentDescription = null,
                    tint = if (result.matched) p.successText else p.dangerText,
                    modifier = Modifier.size(11.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (result.matched) "匹配成功" else "没有匹配",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.matched) p.successText else p.dangerText
                )
                if (result.matched) {
                    Text(
                        listOfNotNull(
                            result.amountCents?.let { "¥${formatMoney(it)}" },
                            result.merchant,
                            result.direction?.let { if (it == "INCOME") "收入" else "支出" }
                        ).joinToString(" · "),
                        fontSize = 10.5.sp,
                        color = p.ink3,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 16.sp
                    )
                } else {
                    Text(
                        result.reason ?: "这条通知不符合规则",
                        fontSize = 10.5.sp,
                        color = p.ink3,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val p = LedgerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.5.sp, color = p.ink3, modifier = Modifier.width(70.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = p.ink,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
        color = LedgerTheme.palette.ink
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.5.sp,
        color = LedgerTheme.palette.ink3,
        modifier = Modifier.padding(top = 10.dp, bottom = 7.dp, start = 2.dp)
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LedgerTheme.palette
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) p.brandSoft else p.card)
            .border(
                1.dp,
                if (selected) p.brand else p.line,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) p.brandSoftText else p.ink2
        )
    }
}
