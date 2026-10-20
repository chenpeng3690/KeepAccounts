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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.repo.TxnDraft
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.CategoryIcons
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.EntryViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 记一笔 / 编辑账单。布局对齐 docs/ui-preview.html 第 02 屏：
 * 标题栏 → 金额（右对齐大字）→ 4 列分类宫格 → 账户 / 时间 / 备注行 → 数字键盘 → 保存。
 *
 * 与设计稿的唯一增补是金额左侧的「支出 / 收入」切换：设计稿只画了支出态，
 * 但没有收入入口这个 App 就没法用，所以压成两个小胶囊塞在金额左边。
 */
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
    var showNoteEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
    LaunchedEffect(accounts, accountId) {
        if (accountId == null) accountId = accounts.firstOrNull()?.id
    }

    val isSpecial = editing?.let {
        it.type == TxType.TRANSFER || it.source == TxSource.ADJUSTMENT
    } == true

    val filtered = categories.filter { it.type == type }
    val amountColor = if (type == TxType.EXPENSE) KaTheme.extra.expense else KaTheme.extra.income

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (vm.isEdit) "编辑账单" else "记一笔",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (vm.isEdit) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (isSpecial) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "转账和余额调整不能在这里改",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这两类记录同时影响两个账户的余额，在记账页改金额会让余额对不上。" +
                    "如需修改请删除后重新操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text("删除这条记录", color = MaterialTheme.colorScheme.error)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeToggle(type = type) { next ->
                    if (next != type) {
                        type = next
                        categoryId = null
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp, end = 3.dp)
                    )
                    Text(
                        text = if (amountText.isEmpty()) "0.00" else amountText,
                        style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            CategoryGrid(
                categories = filtered,
                selectedId = categoryId,
                onSelect = { categoryId = it },
                onMore = { showCategoryPicker = true }
            )

            Spacer(Modifier.height(14.dp))
            FieldRow(
                label = "账户",
                value = accounts.firstOrNull { it.id == accountId }?.name ?: "选择账户",
                onClick = { showAccountPicker = true }
            )
            FieldRow(
                label = "时间",
                value = "${Time.dayLabel(occurredAt)} ${Time.formatTime(occurredAt)}",
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val picked = Calendar.getInstance().apply {
                                timeInMillis = occurredAt
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            occurredAt = picked.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )
            FieldRow(
                label = "备注",
                value = note.ifBlank { "添加备注" },
                muted = note.isBlank(),
                onClick = { showNoteEditor = true }
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        NumPad(onKey = { key -> amountText = applyKey(amountText, key); error = null })

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
                            val result = vm.save(
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
                            if (result == null) onClose() else error = result
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
            categories = filtered,
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

    if (showNoteEditor) {
        NoteDialog(
            initial = note,
            onDismiss = { showNoteEditor = false },
            onConfirm = { note = it; showNoteEditor = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔账单？") },
            text = { Text("删除后进入回收站，默认保留 7 天，期间可以恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = vm.delete()
                        showDeleteConfirm = false
                        if (result == null) onClose() else error = result
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

/* --------------------------- 组件 --------------------------- */

@Composable
private fun TypeToggle(type: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToggleCell("支出", type == TxType.EXPENSE) { onChange(TxType.EXPENSE) }
        ToggleCell("收入", type == TxType.INCOME) { onChange(TxType.INCOME) }
    }
}

@Composable
private fun ToggleCell(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 4 列宫格：前 7 个分类 + 一个「更多」入口 */
@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onMore: () -> Unit
) {
    val shown = categories.take(7)
    val firstRow = shown.take(4)
    val secondRow = shown.drop(4)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            firstRow.forEach { category ->
                Box(Modifier.weight(1f)) {
                    CategoryCell(
                        name = category.name,
                        iconKey = category.icon,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) }
                    )
                }
            }
            repeat(4 - firstRow.size) { Spacer(Modifier.weight(1f)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            secondRow.forEach { category ->
                Box(Modifier.weight(1f)) {
                    CategoryCell(
                        name = category.name,
                        iconKey = category.icon,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) }
                    )
                }
            }
            repeat(3 - secondRow.size) { Spacer(Modifier.weight(1f)) }
            Box(Modifier.weight(1f)) {
                CategoryCell(
                    name = "更多",
                    iconKey = null,
                    selected = false,
                    onClick = onMore
                )
            }
        }
    }
}

@Composable
private fun CategoryCell(
    name: String,
    iconKey: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(15.dp),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            shadowElevation = if (selected) 0.dp else 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (iconKey == null) Icons.Outlined.Apps
                    else CategoryIcons.of(iconKey),
                    contentDescription = name,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    muted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.Medium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun NumPad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

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
                    val isAction = key == "." || key == "⌫"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (isAction) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(
                                imageVector = Icons.Outlined.Backspace,
                                contentDescription = "退格",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = if (isAction) 16.sp else 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isAction) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备注") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 输入规则：最多一个小数点、最多两位小数、不出现前导零堆叠 */
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
