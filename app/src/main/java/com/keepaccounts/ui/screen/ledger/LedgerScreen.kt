package com.keepaccounts.ui.screen.ledger

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.component.ScreenHeader
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.DayGroup
import com.keepaccounts.ui.viewmodel.LedgerUiState
import com.keepaccounts.ui.viewmodel.LedgerViewModel
import com.keepaccounts.util.Money

@Composable
fun LedgerScreen(
    onOpenEntry: () -> Unit,
    onOpenTxn: (Long) -> Unit,
    onOpenSearch: () -> Unit
) {
    val vm = kaViewModel { LedgerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            ScreenHeader(title = "账单") {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            MonthSummaryCard(
                expense = state.monthExpense,
                income = state.monthIncome
            )

            if (state.loaded && state.groups.isEmpty()) {
                EmptyLedger(onOpenEntry)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                ) {
                    state.groups.forEach { group ->
                        item(key = "h-${group.dayStart}") {
                            DayHeader(group)
                        }
                        items(items = group.items, key = { it.id }) { txn ->
                            TxnRow(
                                txn = txn,
                                state = state,
                                onClick = { onOpenTxn(txn.id) }
                            )
                        }
                    }
                }
            }
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

@Composable
private fun MonthSummaryCard(expense: Long, income: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "本月支出",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.titleMedium,
                color = KaTheme.extra.expense,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp, end = 2.dp)
            )
            Text(
                text = Money.format(expense),
                style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
                color = KaTheme.extra.expense,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column {
                Text(
                    text = "本月收入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Money.format(income),
                    style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                    color = KaTheme.extra.income,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column {
                Text(
                    text = "本月结余",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Money.format(income - expense),
                    style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        val parts = buildList {
            if (group.expense > 0) add("支出 ${Money.format(group.expense)}")
            if (group.income > 0) add("收入 ${Money.format(group.income)}")
        }
        Text(
            text = parts.joinToString("  "),
            style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TxnRow(txn: TransactionEntity, state: LedgerUiState, onClick: () -> Unit) {
    val category = txn.categoryId?.let { state.categories[it] }
    val account = state.accounts[txn.accountId]

    val isAdjustment = txn.source == TxSource.ADJUSTMENT
    val title = when {
        isAdjustment -> "余额调整"
        !txn.merchant.isNullOrBlank() -> txn.merchant
        category != null -> category.name
        txn.type == TxType.TRANSFER -> "转账"
        else -> "未分类"
    }
    val subtitle = buildList {
        if (txn.type == TxType.TRANSFER) {
            val to = txn.toAccountId?.let { state.accounts[it] }
            add("转账")
            if (to != null) add("→ ${to.name}")
        } else {
            if (category != null) add(category.name)
            if (account != null) add(account.name)
            if (isAdjustment) add("不计收支")
        }
    }.joinToString(" · ")

    val positive = txn.type == TxType.INCOME || txn.type == TxType.REFUND ||
        (txn.type == TxType.TRANSFER && txn.toAccountId == txn.accountId)
    val amountColor = when {
        isAdjustment -> MaterialTheme.colorScheme.onSurfaceVariant
        txn.type == TxType.TRANSFER -> KaTheme.extra.transfer
        positive -> KaTheme.extra.income
        else -> KaTheme.extra.expense
    }
    val sign = when {
        isAdjustment -> if (txn.type == TxType.INCOME) "+" else "-"
        txn.type == TxType.TRANSFER -> ""
        positive -> "+"
        else -> "-"
    }
    val avatarColor = category?.color ?: if (isAdjustment) "#A0A6B0" else "#6287D8"
    val avatarText = when {
        isAdjustment -> "调"
        category != null -> category.name
        txn.type == TxType.TRANSFER -> "转"
        else -> "?"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(text = avatarText, colorHex = avatarColor, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title ?: "未分类",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = "$sign${Money.format(txn.amount)}",
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            fontWeight = FontWeight.SemiBold,
            color = amountColor
        )
    }
}

@Composable
private fun EmptyLedger(onOpenEntry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
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
            text = "点右下角记第一笔。\n通知自动记账在第 5 步接上。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
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
