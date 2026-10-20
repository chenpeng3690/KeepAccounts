package com.keepaccounts.ui.screen.ledger

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.keepaccounts.notification.NotificationAccess
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.ui.component.LetterSquare
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.DayGroup
import com.keepaccounts.ui.viewmodel.LedgerUiState
import com.keepaccounts.ui.viewmodel.LedgerViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time

/**
 * 账单首页。布局对齐 docs/ui-preview.html 第 01 屏：
 * 标题栏（搜索 / 筛选）→ 月度汇总卡 → 预算条（有预算才出现）→ 白色列表卡（日期分组 + 流水）。
 */
@Composable
fun LedgerScreen(
    onOpenEntry: () -> Unit,
    onOpenTxn: (Long) -> Unit,
    onOpenSearch: () -> Unit
) {
    val vm = kaViewModel { LedgerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 通知使用权可能在系统设置里被关掉，服务会静默失效，所以每次回到页面都重新确认
    var accessGranted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessGranted = NotificationAccess.isGranted(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "账单",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                NavIconButton(Icons.Outlined.Search, "搜索", onOpenSearch)
                Spacer(Modifier.width(8.dp))
                NavIconButton(Icons.Outlined.FilterList, "筛选", onOpenSearch)
            }

            if (!accessGranted) {
                AccessBanner { NotificationAccess.openListenerSettings(context) }
                Spacer(Modifier.height(10.dp))
            }

            MonthSummaryCard(expense = state.monthExpense, income = state.monthIncome)

            state.budgetTotal?.let { total ->
                Spacer(Modifier.height(10.dp))
                BudgetStrip(used = state.monthExpense, total = total)
            }

            if (state.loaded && state.groups.isEmpty()) {
                EmptyLedger(onOpenEntry)
            } else {
                Spacer(Modifier.height(12.dp))
                TransactionCard(state = state, onOpenTxn = onOpenTxn)
            }

            Spacer(Modifier.height(96.dp))
        }

        FloatingActionButton(
            onClick = onOpenEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Add, contentDescription = "记一笔")
        }
    }
}

/** 通知读取被关闭时的常驻提示。服务被系统杀掉后 App 会静默失效，必须一眼看到 */
@Composable
private fun AccessBanner(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFDECEC)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "通知读取已关闭，自动记账已停止",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFC92A2A),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "去开启",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC92A2A),
                modifier = Modifier.clickable(onClick = onClick)
            )
        }
    }
}

@Composable
private fun NavIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MonthSummaryCard(expense: Long, income: Long) {
    val month = Time.toLocal(System.currentTimeMillis()).monthValue

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "$month 月支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = KaTheme.extra.expense,
                        modifier = Modifier.padding(bottom = 3.dp, end = 1.dp)
                    )
                    Text(
                        text = Money.format(expense),
                        style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
                        fontWeight = FontWeight.Bold,
                        color = KaTheme.extra.expense
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "收入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Money.format(income),
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    fontWeight = FontWeight.SemiBold,
                    color = KaTheme.extra.income
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "结余",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Money.format(income - expense),
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BudgetStrip(used: Long, total: Long) {
    if (total <= 0) return
    val ratio = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val remain = (total - used).coerceAtLeast(0)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本月预算已用 ${(ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "剩 ${Money.format(remain)}",
                    style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(3.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun TransactionCard(state: LedgerUiState, onOpenTxn: (Long) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            state.groups.forEach { group ->
                DayHeader(group)
                group.items.forEachIndexed { index, txn ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    TransactionRow(txn = txn, state = state, onClick = { onOpenTxn(txn.id) })
                }
            }
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = if (group.expense > 0) {
                "支出 ¥${Money.format(group.expense)}"
            } else {
                "收入 ¥${Money.format(group.income)}"
            },
            style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionRow(txn: TransactionEntity, state: LedgerUiState, onClick: () -> Unit) {
    val category = txn.categoryId?.let { state.categories[it] }
    val account = state.accounts[txn.accountId]
    val isAdjustment = txn.source == TxSource.ADJUSTMENT
    val isTransfer = txn.type == TxType.TRANSFER
    val incoming = txn.type == TxType.INCOME || txn.type == TxType.REFUND

    val title = when {
        isAdjustment -> "余额调整"
        !txn.merchant.isNullOrBlank() -> txn.merchant.orEmpty()
        !txn.note.isNullOrBlank() -> txn.note.orEmpty()
        category != null -> category.name
        isTransfer -> "转账"
        else -> "未分类"
    }

    val subtitle = buildList {
        if (isTransfer) {
            add("转账")
            txn.toAccountId?.let { to -> state.accounts[to]?.let { add("→ ${it.name}") } }
        } else {
            category?.let { add(it.name) }
            account?.let { add(it.name) }
            if (isAdjustment) add("不计收支")
        }
    }.joinToString(" · ")

    val amountColor = when {
        isAdjustment -> MaterialTheme.colorScheme.onSurfaceVariant
        isTransfer -> KaTheme.extra.transfer
        incoming -> KaTheme.extra.income
        else -> KaTheme.extra.expense
    }
    val signText = when {
        isAdjustment -> if (txn.type == TxType.INCOME) "+" else "-"
        isTransfer -> ""
        incoming -> "+"
        else -> "-"
    }

    // 设计稿：分类色块里放分类名首字，颜色由数据决定，不写死
    val tint = category?.color ?: "#A0A6B0"
    val badge = when {
        isAdjustment -> "调整"
        txn.source == TxSource.NOTIFICATION -> "通知"
        txn.source == TxSource.IMPORT -> "导入"
        else -> "手动"
    }
    val dotColor = when {
        txn.source == TxSource.NOTIFICATION || txn.source == TxSource.IMPORT ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterSquare(
            text = when {
                isAdjustment -> "调"
                isTransfer -> "转"
                else -> category?.name ?: "?"
            },
            colorHex = tint,
            size = 36.dp
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$signText${Money.format(txn.amount)}",
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyLedger(onOpenEntry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "还没有账单",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点右下角记第一笔",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenEntry)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "记一笔",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
