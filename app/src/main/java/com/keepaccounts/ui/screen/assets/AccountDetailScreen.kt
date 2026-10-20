package com.keepaccounts.ui.screen.assets

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.ui.AccountLabels
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.KaChips
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.KaSheetActions
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.AccountDetailViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time
import kotlinx.coroutines.launch

private enum class DetailSheet { Edit, Transfer, Adjust }

@Composable
fun AccountDetailScreen(accountId: Long, onBack: () -> Unit) {
    val vm = kaViewModel { AccountDetailViewModel(it, accountId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var deleteBlockedCount by remember { mutableStateOf(0) }

    val account = state.account

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = account?.name ?: "账户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (account != null) {
                TextButton(onClick = { showDelete = true }) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (account == null) {
            if (state.loaded) {
                Text(
                    text = "账户不存在或已删除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            return@Column
        }

        BalanceCard(account)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("转账", Modifier.weight(1f)) { sheet = DetailSheet.Transfer }
            ActionButton("校准", Modifier.weight(1f)) { sheet = DetailSheet.Adjust }
            ActionButton("编辑", Modifier.weight(1f)) { sheet = DetailSheet.Edit }
        }

        SectionLabel(text = "本月", trailing = Time.formatMonth(System.currentTimeMillis()))
        KaCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric("支出", Money.format(state.monthExpense), KaTheme.extra.expense, Modifier.weight(1f))
                Metric("收入", Money.format(state.monthIncome), KaTheme.extra.income, Modifier.weight(1f))
                Metric(
                    "结余",
                    Money.format(state.monthIncome - state.monthExpense),
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f)
                )
            }
        }

        SectionLabel(text = "最近流水", trailing = "${state.recent.size} 条")
        if (state.recent.isEmpty()) {
            KaCard {
                Text(
                    text = "这个账户还没有流水",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            KaCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                state.recent.forEachIndexed { index, txn ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    TxnRow(txn = txn, accountId = accountId)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    // --- 转账 ---
    if (sheet == DetailSheet.Transfer) {
        TransferSheet(
            from = account,
            targets = state.allAccounts.filter { it.id != accountId },
            onDismiss = { sheet = null },
            onSave = { toId, cents, note ->
                scope.launch {
                    vm.transfer(toId, cents, note)
                    sheet = null
                }
            }
        )
    }

    // --- 校准余额 ---
    if (sheet == DetailSheet.Adjust) {
        AdjustSheet(
            account = account,
            onDismiss = { sheet = null },
            onSave = { actualSigned ->
                scope.launch {
                    vm.adjust(actualSigned)
                    sheet = null
                }
            }
        )
    }

    // --- 编辑 ---
    if (sheet == DetailSheet.Edit) {
        AccountFormSheet(
            account = account,
            existingNames = state.allAccounts.map { it.name },
            onDismiss = { sheet = null },
            onSave = { data ->
                scope.launch {
                    vm.updateProfile(
                        name = data.name,
                        type = data.type,
                        creditLimit = data.creditLimit,
                        billDay = data.billDay,
                        repayDay = data.repayDay,
                        includeInNetWorth = data.includeInNetWorth,
                        color = account.color
                    )
                    sheet = null
                }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除账户？") },
            text = {
                Text(
                    if (deleteBlockedCount > 0) {
                        "这个账户下还有 $deleteBlockedCount 笔流水，不能直接删除。" +
                            "先把流水转移到别的账户，或删除这些流水。"
                    } else {
                        "账户「${account.name}」将被删除，这个操作不会进回收站。"
                    }
                )
            },
            confirmButton = {
                if (deleteBlockedCount > 0) {
                    TextButton(onClick = { showDelete = false }) { Text("知道了") }
                } else {
                    TextButton(onClick = {
                        scope.launch {
                            val blocked = vm.deleteAccount()
                            if (blocked > 0) {
                                deleteBlockedCount = blocked
                            } else {
                                showDelete = false
                                onBack()
                            }
                        }
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                if (deleteBlockedCount == 0) {
                    TextButton(onClick = { showDelete = false }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
private fun BalanceCard(account: AccountEntity) {
    val liability = AccountLabels.isLiability(account.type)
    val brush = Brush.linearGradient(
        listOf(Color(0xFF1F2A44), Color(0xFF33406B), Color(0xFF46528F))
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LetterAvatar(text = account.name, colorHex = account.color, size = 30.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                text = AccountLabels.typeName(account.type),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
            Spacer(Modifier.weight(1f))
            if (!account.cardTail.isNullOrBlank()) {
                Text(
                    text = "尾号 ${account.cardTail}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = AccountLabels.balanceLabel(account.type),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "¥ ${Money.format(if (liability) -account.currentBalance else account.currentBalance)}",
            style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        val limit = account.creditLimit
        if (liability && limit != null && limit > 0) {
            val used = if (account.currentBalance < 0) -account.currentBalance else 0L
            val ratio = (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (ratio > 0.8f) Color(0xFFE03131) else Color(0xFFF5A524))
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "已用 ${Money.format(used)} / ${Money.format(limit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.66f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "可用 ${Money.format((limit - used).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.66f)
                )
            }
        }

        if (account.billDay != null || account.repayDay != null) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (account.billDay != null) {
                    Text(
                        text = "账单日 每月 ${account.billDay} 日",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (account.repayDay != null) {
                    Text(
                        text = "还款日 每月 ${account.repayDay} 日",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TxnRow(txn: TransactionEntity, accountId: Long) {
    val isIncoming = when (txn.type) {
        TxType.INCOME, TxType.REFUND -> true
        TxType.TRANSFER -> txn.toAccountId == accountId
        else -> false
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = txn.merchant ?: txn.note ?: when (txn.type) {
                    TxType.TRANSFER -> "转账"
                    TxType.REFUND -> "退款"
                    TxType.INCOME -> "收入"
                    else -> "支出"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = Time.formatMonthDay(txn.occurredAt) + " " + Time.formatTime(txn.occurredAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = (if (isIncoming) "+" else "-") + Money.format(txn.amount),
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            fontWeight = FontWeight.Medium,
            color = when {
                txn.type == TxType.TRANSFER -> KaTheme.extra.transfer
                isIncoming -> KaTheme.extra.income
                else -> KaTheme.extra.expense
            }
        )
    }
}
