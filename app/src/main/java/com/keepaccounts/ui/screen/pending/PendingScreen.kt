package com.keepaccounts.ui.screen.pending

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.CategoryType
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.KaChips
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.KaSheetActions
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.component.ScreenHeader
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.viewmodel.PendingItem
import com.keepaccounts.ui.viewmodel.PendingViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time

@Composable
fun PendingScreen(
    onManageRules: () -> Unit,
    onOpenLog: () -> Unit
) {
    val vm = kaViewModel { PendingViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PendingItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(title = "待确认") {
            TextButton(onClick = onManageRules) { Text("管理规则") }
        }

        if (state.items.isEmpty()) {
            EmptyState(
                matched = state.weekMatched,
                rejected = state.weekRejected,
                unmatched = state.weekUnmatched,
                recentRejects = state.recentRejects,
                onOpenLog = onOpenLog
            )
        } else {
            BatchBar(count = state.items.size, onConfirmAll = { vm.confirmAll() })
            Spacer(Modifier.height(10.dp))
            state.items.forEach { item ->
                PendingCard(
                    item = item,
                    onConfirm = { vm.confirm(item.txn.id) },
                    onEdit = { editing = item },
                    onReject = { vm.reject(item.txn.id) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { item ->
        PendingEditSheet(
            item = item,
            categories = state.categories,
            accounts = state.accounts,
            onDismiss = { editing = null },
            onConfirm = { patch ->
                vm.confirmEdited(item.txn.id, patch)
                editing = null
            }
        )
    }
}

@Composable
private fun BatchBar(count: Int, onConfirmAll: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count 条待确认",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "全部确认",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onConfirmAll)
            )
        }
    }
}

@Composable
private fun PendingCard(
    item: PendingItem,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onReject: () -> Unit
) {
    val txn = item.txn
    val isExpense = txn.type == TxType.EXPENSE
    val amountColor = if (isExpense) KaTheme.extra.expense else KaTheme.extra.income

    KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterAvatar(text = item.appLabel?.take(1) ?: "?", colorHex = "#3B5BDB", size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.appLabel ?: "未知应用",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SourceBadge(source = item.source, observing = item.isObserving)
        }

        item.rawText?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = (if (isExpense) "-" else "+") + "¥" + Money.format(txn.amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.merchantAndCategory(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                item.account?.let { append(it.name) }
                append(" · ")
                append(Time.formatDateTime(txn.occurredAt))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReject) {
                Text("判为噪音", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("修改", color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(4.dp))
            Surface(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "确认入账",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String, observing: Boolean) {
    val bg = if (observing) KaTheme.extra.warning.copy(alpha = 0.14f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (observing) KaTheme.extra.warning
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            text = source,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

private fun PendingItem.merchantAndCategory(): String {
    val parts = mutableListOf<String>()
    txn.merchant?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    category?.let { parts.add(it.name) }
    return if (parts.isEmpty()) "未分类" else parts.joinToString(" · ")
}

@Composable
private fun EmptyState(
    matched: Int,
    rejected: Int,
    unmatched: Int,
    recentRejects: List<com.keepaccounts.data.local.db.RawEventEntity>,
    onOpenLog: () -> Unit
) {
    KaCard {
        Text(
            text = "没有待确认的账单",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "最近 7 天解析概况",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("已记账", matched, KaTheme.extra.income, Modifier.weight(1f))
            MiniStat("被拦截", rejected, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            MiniStat("未命中", unmatched, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        }
    }

    if (recentRejects.isNotEmpty()) {
        SectionLabel(text = "最近被拦下的通知")
        KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            recentRejects.forEach { e ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = (e.appLabel ?: e.packageName) + " · " + (e.rejectReason ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    SectionLabel(text = "想看每条通知为什么被记账或被拦下？")
    Surface(
        onClick = onOpenLog,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = "打开解析日志",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PendingEditSheet(
    item: PendingItem,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: ((TransactionEntity) -> TransactionEntity) -> Unit
) {
    val txn = item.txn
    var isExpense by remember { mutableStateOf(txn.type != TxType.INCOME) }
    var amountText by remember { mutableStateOf(Money.format(txn.amount)) }
    var categoryId by remember { mutableStateOf(txn.categoryId) }
    var accountId by remember { mutableStateOf(txn.accountId) }
    var error by remember { mutableStateOf<String?>(null) }

    val typeName = if (isExpense) CategoryType.EXPENSE else CategoryType.INCOME
    val filteredCategories = categories.filter { it.type == typeName }
    val categoryNames = filteredCategories.map { it.name }
    val categoryIndex = filteredCategories.indexOfFirst { it.id == categoryId }.coerceAtLeast(0)
    val accountNames = accounts.map { it.name }
    val accountIndex = accounts.indexOfFirst { it.id == accountId }.coerceAtLeast(0)

    KaSheet(title = "修改后入账", onDismiss = onDismiss) {
        KaChips(
            options = listOf("支出", "收入"),
            selectedIndex = if (isExpense) 0 else 1,
            onSelect = { isExpense = it == 0 }
        )
        Spacer(Modifier.height(12.dp))
        KaField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "金额",
            prefix = "¥",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            isError = error != null
        )
        Spacer(Modifier.height(14.dp))
        SectionLabel(text = "分类")
        KaChips(
            options = categoryNames,
            selectedIndex = categoryIndex,
            onSelect = { categoryId = filteredCategories.getOrNull(it)?.id }
        )
        Spacer(Modifier.height(14.dp))
        SectionLabel(text = "账户")
        KaChips(
            options = accountNames,
            selectedIndex = accountIndex,
            onSelect = { accounts.getOrNull(it)?.id?.let { id -> accountId = id } }
        )

        KaSheetActions(
            confirmText = "确认入账",
            onConfirm = {
                val cents = Money.parse(amountText)
                if (cents == null || cents <= 0) {
                    error = "金额格式不对"
                } else {
                    error = null
                    onConfirm { t ->
                        t.copy(
                            type = if (isExpense) TxType.EXPENSE else TxType.INCOME,
                            amount = cents,
                            categoryId = categoryId,
                            accountId = accountId
                        )
                    }
                }
            },
            onDismiss = onDismiss,
            errorText = error
        )
    }
}
