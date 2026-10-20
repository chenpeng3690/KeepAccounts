package com.keepaccounts.ui.screen.entry

import android.app.DatePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.repo.TxnDraft
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.EntryViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun EntryScreen(
    txnId: Long?,
    onClose: () -> Unit,
    onManageCategories: () -> Unit
) {
    val vm = kaViewModel { EntryViewModel(it, txnId) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var occurredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 编辑模式：数据到达后回填一次
    LaunchedEffect(editing?.id) {
        editing?.let { t ->
            type = if (t.type == TxType.INCOME || t.type == TxType.REFUND) TxType.INCOME else t.type
            amountText = Money.format(t.amount).replace(",", "")
            categoryId = t.categoryId
            accountId = t.accountId
            occurredAt = t.occurredAt
            note = t.note.orEmpty()
        }
    }
    // 新建时默认选中第一个账户
    LaunchedEffect(accounts, accountId) {
        if (accountId == null) accountId = accounts.firstOrNull()?.id
    }

    val isSpecial = editing?.let {
        it.type == TxType.TRANSFER || it.source == TxSource.ADJUSTMENT
    } == true

    val filteredCategories = categories.filter { it.type == type }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // --- 顶栏 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (vm.isEdit) "编辑账单" else "记一笔",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (vm.isEdit) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (isSpecial) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "转账和余额调整记录不能在记账页编辑",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这两类记录涉及两个账户的余额对冲，直接在记账页改金额会让余额对不上。" +
                    "如需修改请删除后重新操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text("删除这条记录", color = MaterialTheme.colorScheme.error)
            }
            return@Column
        }

        // --- 收支切换 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TypePill(
                text = "支出",
                selected = type == TxType.EXPENSE,
                modifier = Modifier.width(96.dp)
            ) {
                type = TxType.EXPENSE
                categoryId = null
            }
            Spacer(Modifier.width(10.dp))
            TypePill(
                text = "收入",
                selected = type == TxType.INCOME,
                modifier = Modifier.width(96.dp)
            ) {
                type = TxType.INCOME
                categoryId = null
            }
        }

        // --- 金额 ---
        Text(
            text = if (amountText.isEmpty()) "0.00" else amountText,
            style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
            fontWeight = FontWeight.Bold,
            color = if (type == TxType.EXPENSE) KaTheme.extra.expense else KaTheme.extra.income,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 18.dp)
        )

        // --- 中间信息区（可滚动，避免小屏被键盘挤没） ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            val selectedCategory = filteredCategories.firstOrNull { it.id == categoryId }
            InfoRow(
                label = "分类",
                value = selectedCategory?.name ?: "未分类",
                onClick = { showCategoryPicker = true }
            )
            InfoRow(
                label = "账户",
                value = accounts.firstOrNull { it.id == accountId }?.name ?: "选择账户",
                onClick = { showAccountPicker = true }
            )
            InfoRow(
                label = "日期",
                value = Time.formatDate(occurredAt),
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val c = Calendar.getInstance().apply {
                                timeInMillis = occurredAt
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            occurredAt = c.timeInMillis
                            error = null
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("备注（选填）", style = MaterialTheme.typography.bodySmall)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // --- 键盘 ---
        Keypad(
            onKey = { key ->
                amountText = applyKey(amountText, key)
                error = null
            }
        )

        // --- 保存 ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    val cents = Money.parse(amountText)
                    val acc = accountId
                    when {
                        cents == null || cents <= 0 -> error = "请输入金额"
                        acc == null -> error = "请选择账户"
                        else -> scope.launch {
                            val message = vm.save(
                                TxnDraft(
                                    type = type,
                                    amount = cents,
                                    accountId = acc,
                                    categoryId = categoryId,
                                    note = note.takeIf { it.isNotBlank() },
                                    occurredAt = occurredAt,
                                    source = editing?.source ?: TxSource.MANUAL
                                )
                            )
                            if (message == null) onClose() else error = message
                        }
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (vm.isEdit) "保存修改" else "保存",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = filteredCategories,
            selectedId = categoryId,
            onSelect = { categoryId = it; showCategoryPicker = false },
            onManage = { showCategoryPicker = false; onManageCategories() },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = accountId,
            onSelect = { accountId = it; showAccountPicker = false },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔账单？") },
            text = { Text("删除后会进回收站，默认保留 7 天，期间可以恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val message = vm.delete()
                        showDeleteConfirm = false
                        if (message == null) onClose() else error = message
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TypePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val keys = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        ".", "0", "⌫"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (key == "⌫") FontWeight.Normal else FontWeight.SemiBold,
                            color = if (key == "⌫") MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** 键盘输入规则：最多一个小数点、最多两位小数、不允许前导零堆叠 */
private fun applyKey(current: String, key: String): String {
    return when (key) {
        "⌫" -> current.dropLast(1)
        "." -> when {
            current.isEmpty() -> "0."
            current.contains(".") -> current
            else -> "$current."
        }
        else -> {
            val dot = current.indexOf('.')
            if (dot >= 0 && current.length - dot > 2) return current
            if (current == "0") key else current + key
        }
    }
}
