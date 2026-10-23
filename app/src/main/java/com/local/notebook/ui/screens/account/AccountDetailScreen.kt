package com.local.notebook.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.ui.components.CategoryTile
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.util.formatMoney
import com.local.notebook.ui.util.formatMoneyShort
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEditAccount: (Long) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val account = state.account

    var showActions by remember { mutableStateOf(false) }
    var showCalibrate by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var calibrateInput by remember { mutableStateOf("") }
    var transferFrom by remember { mutableStateOf(-1L) }
    var transferTo by remember { mutableStateOf(-1L) }
    var transferAmount by remember { mutableStateOf("") }
    var transferNote by remember { mutableStateOf("") }
    var deleteCount by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    if (account == null) {
        if (!state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("账户不存在", fontSize = 13.sp, color = p.ink3)
            }
        }
        return
    }

    val liability = isLiabilityType(account.type)
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
                onClick = onBack
            )
            Text(
                "账户详情",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            RoundIconButton(
                icon = R.drawable.ic_more,
                contentDescription = "更多操作",
                onClick = { showActions = true }
            )
        }

        if (liability) {
            CreditCard(account)
        } else {
            BalanceCard(account)
        }

        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.card)
                    .clickable {
                        calibrateInput = plainMoney(
                            if (liability) abs(account.currentBalance) else account.currentBalance
                        )
                        showCalibrate = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("校准余额", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.brand)
                    .clickable {
                        val others = state.allAccounts.filter { it.id != account.id }
                        val nonLiability = others.filterNot { isLiabilityType(it.type) }
                        if (liability) {
                            transferFrom = (nonLiability.firstOrNull() ?: others.firstOrNull())?.id ?: -1L
                            transferTo = account.id
                            transferAmount = plainMoney(abs(account.currentBalance))
                        } else {
                            transferFrom = account.id
                            transferTo = others.firstOrNull()?.id ?: -1L
                            transferAmount = ""
                        }
                        transferNote = ""
                        showTransfer = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (liability) "还款" else "转账",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        if (liability && (account.billDay != null || account.repayDay != null)) {
            LedgerCard(
                modifier = Modifier.padding(top = 11.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "账单日 ${account.billDay?.let { "每月 $it 日" } ?: "未设置"}",
                        fontSize = 11.5.sp,
                        color = p.ink2
                    )
                    Text(
                        "还款日 ${account.repayDay?.let { "每月 $it 日" } ?: "未设置"}",
                        fontSize = 11.5.sp,
                        color = p.ink2
                    )
                }
            }
        }

        Text(
            "本月流水 ${state.monthCount} 笔",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.ink3,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 2.dp)
        )
        if (state.transactions.isEmpty()) {
            LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(16.dp)) {
                Text(
                    "这个账户还没有流水",
                    fontSize = 11.sp,
                    color = p.ink3,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LedgerCard(
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
            ) {
                state.transactions.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 1.dp, color = p.divider)
                    }
                    AccountTxnRow(item)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            containerColor = p.card,
            title = { Text(account.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Column {
                    ActionText("编辑账户", p.ink) {
                        showActions = false
                        onEditAccount(account.id)
                    }
                    ActionText("删除账户", p.dangerText) {
                        showActions = false
                        scope.launch {
                            val count = viewModel.transactionCount()
                            if (count == 0) {
                                showDeleteConfirm = true
                            } else {
                                deleteCount = count
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActions = false }) { Text("关闭", color = p.ink2) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = p.card,
            title = { Text("删除这个账户？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Text(
                    "账户没有未删除的账单，删除后无法恢复。",
                    fontSize = 12.sp,
                    color = p.ink2,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAccount()
                }) { Text("删除", color = p.dangerText) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = p.ink2) }
            }
        )
    }

    deleteCount?.let { count ->
        AlertDialog(
            onDismissRequest = { deleteCount = null },
            containerColor = p.card,
            title = { Text("无法直接删除", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Text(
                    "该账户下还有 $count 笔未删除的账单。请先把这些账单转移到其他账户，或先删除它们。",
                    fontSize = 12.sp,
                    color = p.ink2,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteCount = null
                    showTargetPicker = true
                }) { Text("转移到其他账户", color = p.brand) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCount = null }) { Text("取消", color = p.ink2) }
            }
        )
    }

    if (showTargetPicker) {
        val targets = state.allAccounts.filter { it.id != account.id }
        AlertDialog(
            onDismissRequest = { showTargetPicker = false },
            containerColor = p.card,
            title = { Text("转移到哪个账户？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Column {
                    if (targets.isEmpty()) {
                        Text("没有其他账户可选", fontSize = 12.sp, color = p.ink3)
                    }
                    targets.forEach { target ->
                        ActionText(target.name, p.ink) {
                            showTargetPicker = false
                            viewModel.moveTransactionsAndDelete(target.id)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTargetPicker = false }) { Text("取消", color = p.ink2) }
            }
        )
    }

    if (showCalibrate) {
        AlertDialog(
            onDismissRequest = { showCalibrate = false },
            containerColor = p.card,
            title = {
                Text(
                    if (liability) "校准欠款" else "校准余额",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink
                )
            },
            text = {
                Column {
                    Text(
                        if (liability) "输入当前实际欠款（正数），系统会生成一条余额调整记录。"
                        else "输入当前实际余额，系统会生成一条不计收支的余额调整记录。",
                        fontSize = 11.5.sp,
                        color = p.ink2,
                        lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = calibrateInput,
                        onValueChange = { calibrateInput = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        placeholder = { Text("0.00", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cents = calibrateInput.trim()
                        .toBigDecimalOrNull()
                        ?.setScale(2, RoundingMode.HALF_UP)
                        ?.movePointRight(2)
                        ?.toLong()
                    if (cents != null) {
                        val target = if (liability) -abs(cents) else cents
                        viewModel.calibrate(target)
                        showCalibrate = false
                    }
                }) { Text("保存", color = p.brand) }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrate = false }) { Text("取消", color = p.ink2) }
            }
        )
    }

    if (showTransfer) {
        TransferDialog(
            accounts = state.allAccounts,
            fromId = transferFrom,
            toId = transferTo,
            amount = transferAmount,
            note = transferNote,
            onFrom = { transferFrom = it },
            onTo = { transferTo = it },
            onAmount = { transferAmount = it },
            onNote = { transferNote = it },
            onDismiss = { showTransfer = false },
            onConfirm = {
                val cents = transferAmount.trim()
                    .toBigDecimalOrNull()
                    ?.setScale(2, RoundingMode.HALF_UP)
                    ?.movePointRight(2)
                    ?.toLong()
                if (cents != null && cents > 0 && transferFrom > 0 && transferTo > 0 &&
                    transferFrom != transferTo
                ) {
                    viewModel.transfer(transferFrom, transferTo, cents, transferNote)
                    showTransfer = false
                }
            }
        )
    }
}

@Composable
private fun BalanceCard(account: AccountEntity) {
    val p = LedgerTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(p.netGradient))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(account.name, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Text(
                accountTypeLabel(account.type),
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        Text(
            "账户余额",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "¥ ${formatMoney(account.currentBalance)}",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.1).sp,
            color = Color.White,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            if (account.includeInNetWorth) "计入净资产" else "不计入净资产",
            fontSize = 10.5.sp,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CreditCard(account: AccountEntity) {
    val p = LedgerTheme.palette
    val used = abs(account.currentBalance)
    val limit = account.creditLimit ?: 0L
    val available = (limit - used).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(p.creditGradient))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(account.name, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            Text(
                accountTypeLabel(account.type),
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
        Text(
            if (account.repayDay != null) "每月 ${account.repayDay} 日还款" else "信用账户",
            fontSize = 10.5.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            "当前欠款",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "¥ ${formatMoney(used)}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (limit > 0) {
            val progress = (used.toFloat() / limit).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFF5A524))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "已用 ${formatMoneyShort(used)} / ${formatMoneyShort(limit)}",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.68f)
                )
                Text(
                    "可用 ${formatMoneyShort(available)}",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
        }
    }
}

@Composable
private fun AccountTxnRow(item: AccountDetailTxnUi) {
    val p = LedgerTheme.palette
    val row = item.row
    val adjustment = row.source == "ADJUSTMENT"
    val transfer = row.type == "TRANSFER"
    val tone = when {
        transfer -> LedgerTone.PURPLE
        adjustment -> LedgerTone.GRAY
        else -> runCatching { LedgerTone.valueOf(row.categoryTone ?: "GRAY") }
            .getOrDefault(LedgerTone.GRAY)
    }
    val char = when {
        transfer -> "转"
        adjustment -> "调"
        else -> row.categoryIcon ?: "其"
    }
    val amountColor = when {
        adjustment -> p.ink3
        item.signedAmount >= 0 -> p.income
        else -> p.expense
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryTile(text = char, tone = tone)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.merchant?.takeIf { it.isNotBlank() }
                    ?: row.note?.takeIf { it.isNotBlank() }
                    ?: if (transfer) "转账" else "未命名账单",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = p.ink,
                maxLines = 1
            )
            Text(
                item.displayCategory,
                fontSize = 10.5.sp,
                color = p.ink3,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            (if (item.signedAmount >= 0) "+" else "-") + formatMoney(abs(item.signedAmount)),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = amountColor
        )
    }
}

@Composable
private fun TransferDialog(
    accounts: List<AccountEntity>,
    fromId: Long,
    toId: Long,
    amount: String,
    note: String,
    onFrom: (Long) -> Unit,
    onTo: (Long) -> Unit,
    onAmount: (String) -> Unit,
    onNote: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("转账", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("从", fontSize = 10.5.sp, color = p.ink3)
                AccountChips(accounts, fromId, onFrom)
                Text("到", fontSize = 10.5.sp, color = p.ink3, modifier = Modifier.padding(top = 10.dp))
                AccountChips(accounts, toId, onTo)
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmount,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    placeholder = { Text("金额", fontSize = 12.sp) }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = onNote,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    placeholder = { Text("备注（可选）", fontSize = 12.sp) }
                )
                Text(
                    "转账不计入收支统计，只调整两个账户的余额。",
                    fontSize = 10.sp,
                    color = p.ink3,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认转账", color = p.brand) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = p.ink2) }
        }
    )
}

@Composable
private fun AccountChips(accounts: List<AccountEntity>, selectedId: Long, onSelect: (Long) -> Unit) {
    val p = LedgerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        accounts.forEach { account ->
            val selected = account.id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) p.brandSoft else p.cardAlt)
                    .clickable { onSelect(account.id) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    account.name,
                    fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) p.brandSoftText else p.ink2
                )
            }
        }
    }
}

@Composable
private fun ActionText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

private fun plainMoney(cents: Long): String =
    BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
