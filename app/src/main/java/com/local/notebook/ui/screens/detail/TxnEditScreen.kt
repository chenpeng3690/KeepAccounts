package com.local.notebook.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import com.local.notebook.ui.components.SectionHeader
import com.local.notebook.ui.components.TypeToggle
import com.local.notebook.ui.components.showDateTimePicker
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.formatDateTimeLabel
import com.local.notebook.ui.util.formatFullDateTime
import com.local.notebook.ui.util.formatMoney
import org.json.JSONObject

@Composable
fun TxnEditScreen(
    onClose: () -> Unit,
    viewModel: TxnEditViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }

    val typeCategories = state.categories.filter { it.type == state.type }
    val categoryName = typeCategories.firstOrNull { it.id == state.categoryId }?.name ?: "未分类"
    val accountName = state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "请选择"
    val amountColor = if (state.type == "INCOME") p.income else p.expense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(
                icon = R.drawable.ic_chevron_left,
                contentDescription = "返回",
                onClick = onClose
            )
            Text(
                if (state.confirmMode) "确认账单" else "账单详情",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            if (state.exists && !state.loading) {
                RoundIconButton(
                    icon = R.drawable.ic_trash,
                    contentDescription = "删除账单",
                    onClick = { showDeleteConfirm = true }
                )
            } else {
                Spacer(Modifier.size(30.dp))
            }
        }

        if (state.loading) {
            Spacer(Modifier.height(80.dp))
            return@Column
        }
        if (!state.exists) {
            Text(
                "这笔账单不存在或已被彻底删除",
                fontSize = 13.sp,
                color = p.ink3,
                modifier = Modifier.padding(top = 60.dp)
            )
            return@Column
        }

        LedgerCard(shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(16.dp)) {
            Text("金额（元）", fontSize = 11.sp, color = p.ink3)
            BasicTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.2).sp,
                    color = amountColor,
                    textAlign = TextAlign.End
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(p.brand)
            )
        }

        Spacer(Modifier.height(10.dp))
        TypeToggle(type = state.type, onTypeChange = viewModel::setType)

        Spacer(Modifier.height(12.dp))
        FormRow(label = "分类", value = categoryName, onClick = { showCategorySheet = true })
        Spacer(Modifier.height(8.dp))
        FormRow(label = "账户", value = accountName, onClick = { showAccountSheet = true })
        Spacer(Modifier.height(8.dp))
        FormTextFieldRow(
            label = "商户",
            value = state.merchant,
            onValueChange = viewModel::setMerchant,
            placeholder = "未填写"
        )
        Spacer(Modifier.height(8.dp))
        FormTextFieldRow(
            label = "备注",
            value = state.note,
            onValueChange = viewModel::setNote,
            placeholder = "添加备注…"
        )
        Spacer(Modifier.height(8.dp))
        FormRow(
            label = "时间",
            value = formatDateTimeLabel(state.occurredAt),
            onClick = {
                showDateTimePicker(context, state.occurredAt) { viewModel.setOccurredAt(it) }
            }
        )

        state.error?.let { error ->
            Text(
                error,
                fontSize = 10.5.sp,
                color = p.expense,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "来源：${sourceLabel(state.source)} · 已编辑 ${state.editCount} 次",
                fontSize = 10.5.sp,
                color = p.ink3
            )
            Text(
                "创建后仍可修改",
                fontSize = 10.5.sp,
                color = p.ink3.copy(alpha = 0.7f)
            )
        }

        if (state.logs.isNotEmpty()) {
            SectionHeader(title = "编辑历史", trailing = "${state.logs.size} 条")
            LedgerCard(
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                state.logs.forEachIndexed { index, log ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(p.divider)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "修改账单",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = p.ink
                            )
                            Text(
                                formatFullDateTime(log.editedAt),
                                fontSize = 10.5.sp,
                                color = p.ink3,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            historyAmountChange(log.beforeJson, log.afterJson),
                            fontSize = 11.5.sp,
                            color = p.ink2
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (state.saving) p.brand.copy(alpha = 0.5f) else p.brand)
                .clickable(enabled = !state.saving) { viewModel.save() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    state.saving -> "保存中…"
                    state.confirmMode -> "保存并确认入账"
                    else -> "保存修改"
                },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Text(
            "删除这笔账单",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = p.dangerText,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDeleteConfirm = true }
                .padding(vertical = 14.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showCategorySheet) {
        CategoryPickerSheet(
            categories = typeCategories,
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
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = p.card,
            title = { Text("删除这笔账单？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Text(
                    "账单会先进入回收站，7 天内可以恢复。账户余额也会同步回滚。",
                    fontSize = 12.sp,
                    color = p.ink2,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("删除", color = p.dangerText) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = p.ink2) }
            }
        )
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "NOTIFICATION" -> "通知自动记账"
    "IMPORT" -> "导入"
    "TEMPLATE" -> "周期模板"
    "ADJUSTMENT" -> "余额调整"
    else -> "手动记录"
}

private fun historyAmountChange(beforeJson: String, afterJson: String): String = runCatching {
    val before = JSONObject(beforeJson).optLong("amount")
    val after = JSONObject(afterJson).optLong("amount")
    if (before == after) "其他信息" else "${formatMoney(before)} → ${formatMoney(after)}"
}.getOrDefault("信息变更")
