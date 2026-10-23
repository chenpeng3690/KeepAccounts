package com.local.notebook.ui.screens.add

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.ui.components.AccountPickerSheet
import com.local.notebook.ui.components.CategoryPickerSheet
import com.local.notebook.ui.components.FormRow
import com.local.notebook.ui.components.FormTextFieldRow
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.TypeToggle
import com.local.notebook.ui.components.showDateTimePicker
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.theme.containerColor
import com.local.notebook.ui.theme.contentColor
import com.local.notebook.ui.util.formatDateTimeLabel

@Composable
fun AddTransactionScreen(
    onClose: () -> Unit,
    viewModel: AddTxnViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }

    val typeCategories = state.categories.filter { it.type == state.type }
    val selectedCategory = typeCategories.firstOrNull { it.id == state.categoryId }
    val accountName = state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "请选择"
    val income = state.type == "INCOME"
    val amountColor = if (income) p.income else p.expense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                "记一笔",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(30.dp))
        }

        TypeToggle(type = state.type, onTypeChange = viewModel::setType)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = if (state.expression.isEmpty()) "0.00" else state.expression,
                fontSize = when {
                    state.expression.length > 14 -> 24.sp
                    state.expression.length > 9 -> 30.sp
                    else -> 38.sp
                },
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.6).sp,
                color = amountColor,
                maxLines = 1
            )
            Text(
                text = "¥ ${state.amountPreview}",
                fontSize = 11.sp,
                color = p.ink3,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        CategoryGrid(
            categories = typeCategories.take(7),
            selectedId = state.categoryId,
            onSelect = viewModel::selectCategory,
            onMore = { showCategorySheet = true }
        )

        Spacer(Modifier.height(14.dp))

        FormRow(
            label = "账户",
            value = accountName,
            onClick = { showAccountSheet = true }
        )
        if (state.accounts.isEmpty()) {
            Text(
                "还没有账户，请先在资产页添加一个账户",
                fontSize = 10.5.sp,
                color = p.ink3,
                modifier = Modifier.padding(start = 2.dp, top = 5.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        FormRow(
            label = "时间",
            value = formatDateTimeLabel(state.occurredAt),
            onClick = {
                showDateTimePicker(context, state.occurredAt) { viewModel.setOccurredAt(it) }
            }
        )
        Spacer(Modifier.height(8.dp))
        FormTextFieldRow(
            label = "备注",
            value = state.note,
            onValueChange = viewModel::setNote,
            placeholder = "添加备注…"
        )

        state.error?.let { error ->
            Text(
                text = error,
                fontSize = 10.5.sp,
                color = p.expense,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        OperatorRow(onKey = viewModel::onKey)
        Spacer(Modifier.height(8.dp))
        NumberPad(onKey = viewModel::onKey)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GhostButton(
                text = "再记一笔",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.save(keepGoing = true) }
            )
            PrimaryButton(
                text = if (state.saving) "保存中…" else "保存",
                modifier = Modifier.weight(1f),
                enabled = !state.saving,
                onClick = { viewModel.save(keepGoing = false) }
            )
        }
        Spacer(Modifier.height(20.dp))
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
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onMore: () -> Unit
) {
    val p = LedgerTheme.palette
    val cells = buildList {
        addAll(categories)
        add(null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { category ->
                    if (category == null) {
                        CategoryCell(
                            label = "更多",
                            iconContent = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_grid),
                                    contentDescription = null,
                                    tint = p.ink2,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            selected = false,
                            onClick = onMore,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val selected = category.id == selectedId
                        val tone = runCatching { LedgerTone.valueOf(category.tone) }
                            .getOrDefault(LedgerTone.GRAY)
                        CategoryCell(
                            label = category.name,
                            iconContent = {
                                Text(
                                    category.icon,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else tone.contentColor()
                                )
                            },
                            selected = selected,
                            tileColor = if (selected) p.brand else tone.containerColor(),
                            onClick = { onSelect(category.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    label: String,
    iconContent: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileColor: Color = LedgerTheme.palette.card
) {
    val p = LedgerTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Text(
            label,
            fontSize = 10.5.sp,
            color = if (selected) p.brand else p.ink2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 5.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun OperatorRow(onKey: (String) -> Unit) {
    val p = LedgerTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("+" to "+", "-" to "−", "×" to "×", "÷" to "÷").forEach { (key, label) ->
            Surface(
                onClick = { onKey(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                shape = RoundedCornerShape(11.dp),
                color = p.track
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.ink2)
                }
            }
        }
    }
}

@Composable
private fun NumberPad(onKey: (String) -> Unit) {
    val p = LedgerTheme.palette
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "del")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowKeys.forEach { key ->
                    Surface(
                        onClick = { onKey(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = if (key == "del") p.cardAlt else p.card,
                        shadowElevation = if (p.isDark) 0.dp else 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "del") {
                                Text("⌫", fontSize = 19.sp, color = p.ink2)
                            } else {
                                Text(key, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = LedgerTheme.palette
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) p.brand else p.brand.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LedgerTheme.palette
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
    }
}
