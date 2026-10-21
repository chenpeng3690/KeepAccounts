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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.ui.component.KaBadge
import com.keepaccounts.ui.component.KaBadgeStyle
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.KaChips
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.KaSheetActions
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.component.MiniButton
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
        // nav：标题 + 橙色数量 + 批量处理
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "待确认",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (state.items.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${state.items.size}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = KaTheme.extra.warning
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.items.isNotEmpty()) {
                Text(
                    text = "批量处理",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { vm.confirmAll() }
                        .padding(4.dp)
                )
            }
        }

        if (state.items.isEmpty()) {
            EmptyState(
                matched = state.weekMatched,
                rejected = state.weekRejected,
                recentRejects = state.recentRejects,
                onOpenLog = onOpenLog
            )
        } else {
            // 顶部横幅
            Banner()

            state.items.forEach { item ->
                PendCard(
                    item = item,
                    onConfirm = { vm.confirm(item.txn.id) },
                    onEdit = { editing = item },
                    onReject = { vm.reject(item.txn.id) }
                )
                Spacer(Modifier.height(9.dp))
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = "规则不准？去管理解析规则 ›",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onManageRules)
                    .padding(vertical = 4.dp)
            )
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
private fun Banner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(13.dp),
        color = KaTheme.extra.warning.copy(alpha = 0.10f)
    ) {
        Text(
            text = "通用规则猜的，不确定，所以没直接入账。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8A5205),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun PendCard(
    item: PendingItem,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onReject: () -> Unit
) {
    val txn = item.txn
    val isExpense = txn.type == TxType.EXPENSE
    val merchant = txn.merchant
    val categoryName = item.category?.name

    KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        // 头：app 图标 + 应用 · 时间 + 来源 badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterAvatar(text = item.appLabel?.take(1) ?: "?", colorHex = "#3B5BDB", size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${item.appLabel ?: "未知"} · ${Time.formatTime(txn.occurredAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                item.isObserving -> KaBadge("规则观察中", KaBadgeStyle.INFO)
                item.source == "通用兜底" -> KaBadge("通用规则", KaBadgeStyle.WATCH)
            }
        }

        // 原文
        item.rawText?.let {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                color = Color(0xFFF7F8FA)
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 抽取结果
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExtractChip("金额 ${Money.format(txn.amount)}", ChipKind.OK)
            ExtractChip(
                merchant?.let { "商户 $it" } ?: "商户 未识别",
                if (merchant != null) ChipKind.OK else ChipKind.WARN
            )
            ExtractChip(if (isExpense) "支出" else "收入", ChipKind.NORMAL)
            categoryName?.let { ExtractChip(it, ChipKind.NORMAL) }
        }

        // 操作
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MiniButton("判为噪音", onClick = onReject, modifier = Modifier.weight(1f), danger = true)
            MiniButton("修改", onClick = onEdit, modifier = Modifier.weight(1f))
            MiniButton("确认入账", onClick = onConfirm, modifier = Modifier.weight(1f), primary = true)
        }
    }
}

private enum class ChipKind { OK, WARN, NORMAL }

@Composable
private fun ExtractChip(text: String, kind: ChipKind) {
    val (bg, fg) = when (kind) {
        ChipKind.OK -> Color(0xFFE7F7F1) to Color(0xFF067A57)
        ChipKind.WARN -> Color(0xFFFDF3E3) to Color(0xFF8A5205)
        ChipKind.NORMAL -> Color(0xFFF4F5F8) to Color(0xFF5B6270)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyState(
    matched: Int,
    rejected: Int,
    recentRejects: List<RawEventEntity>,
    onOpenLog: () -> Unit
) {
    // 对勾 + 标题
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(Color(0xFFE7F7F1), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF0CA678),
                modifier = Modifier.size(25.dp)
            )
        }
        Spacer(Modifier.height(11.dp))
        Text(
            text = "都处理完了",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "自动记账正常工作中",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 最近 7 天解析情况
    Spacer(Modifier.height(14.dp))
    KaCard {
        Text(
            text = "最近 7 天解析情况",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekStat(matched, "自动入账", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            WeekDivider()
            WeekStat(rejected, "已拦噪音", Color(0xFF8A91A0), Modifier.weight(1f))
            WeekDivider()
            WeekStat(0, "待确认", Color(0xFF12B886), Modifier.weight(1f))
        }
    }

    // 最近被拦下的通知
    if (recentRejects.isNotEmpty()) {
        SectionLabel(text = "最近被拦下的通知")
        KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
            recentRejects.forEach { e ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LetterAvatar(text = (e.appLabel ?: e.packageName).take(1), colorHex = "#3B5BDB", size = 36.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = e.title ?: (e.appLabel ?: e.packageName),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = e.rejectReason ?: "被拦截",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Time.dayLabel(e.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        text = "查看全部解析日志 ›",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLog)
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun WeekStat(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeekDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(Color(0xFFF0F1F5))
    )
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
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
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
