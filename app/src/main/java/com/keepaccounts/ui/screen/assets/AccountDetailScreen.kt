package com.keepaccounts.ui.screen.assets

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.ui.AccountLabels
import com.keepaccounts.ui.component.KaIconButton
import com.keepaccounts.ui.component.LetterSquare
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.AccountDetailViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class DetailSheet { Edit, Transfer, Adjust }

/**
 * 账户详情。对齐 docs/ui-preview.html 第 05 屏：
 * 深色账户卡（欠款 / 额度进度）→ 校准 + 还款两个按钮 → 账单日还款日 → 本月流水。
 */
@Composable
fun AccountDetailScreen(accountId: Long, onBack: () -> Unit) {
    val vm = kaViewModel { AccountDetailViewModel(it, accountId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
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
                .padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KaIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Spacer(Modifier.weight(1f))
            Box {
                KaIconButton(Icons.Outlined.MoreVert, "更多") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑账户") },
                        onClick = { menuOpen = false; sheet = DetailSheet.Edit }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("删除账户", color = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; showDelete = true }
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
        } else {
            val liability = AccountLabels.isLiability(account.type)
            AccountHeadCard(account, liability)

            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GhostButton("校准余额", Modifier.weight(1f)) { sheet = DetailSheet.Adjust }
                PrimaryButton(
                    text = if (liability) "还款" else "转账",
                    modifier = Modifier.weight(1f)
                ) { sheet = DetailSheet.Transfer }
            }

            if (account.billDay != null || account.repayDay != null) {
                Spacer(Modifier.height(11.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "账单日 " + (account.billDay?.let { "每月 $it 日" } ?: "未设置"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "还款日 " + (account.repayDay?.let { "每月 $it 日" } ?: "未设置"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近流水",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${state.recent.size} 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (state.recent.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "这个账户还没有流水",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                        state.recent.forEachIndexed { index, txn ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                            RecentRow(
                                txn = txn,
                                state = state,
                                accountId = accountId,
                                liability = liability
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (account == null) return

    if (sheet == DetailSheet.Transfer) {
        TransferSheet(
            fixedAccount = account,
            candidates = state.allAccounts.filter { it.id != accountId },
            fixedIsSource = !AccountLabels.isLiability(account.type),
            onDismiss = { sheet = null },
            onSave = { fromId, toId, cents, note ->
                scope.launch {
                    vm.transferBetween(fromId, toId, cents, note)
                    sheet = null
                }
            }
        )
    }

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
                        "这个账户下还有 $deleteBlockedCount 笔流水，不能直接删除。"
                    } else {
                        "账户「${account.name}」将被删除，此操作不进回收站。"
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

/* --------------------------- 组件 --------------------------- */

@Composable
private fun AccountHeadCard(account: AccountEntity, liability: Boolean) {
    val brush = Brush.linearGradient(
        0f to Color(0xFF1F2A44),
        0.55f to Color(0xFF33406B),
        1f to Color(0xFF46528F)
    )
    val shown = if (liability) -account.currentBalance else account.currentBalance
    val limit = account.creditLimit

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
            Text(
                text = "${account.name} · ${AccountLabels.typeName(account.type)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.weight(1f))
            if (!account.cardTail.isNullOrBlank()) {
                Text(
                    text = account.cardTail,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }

        if (!account.cardTail.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "**** **** **** ${account.cardTail}",
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = AccountLabels.balanceLabel(account.type),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "¥ ${Money.format(shown)}",
            style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (liability && limit != null && limit > 0) {
            val ratio = (shown.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
            Spacer(Modifier.height(14.dp))
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
                        .background(
                            when {
                                ratio >= 0.9f -> Color(0xFFE03131)
                                ratio >= 0.7f -> Color(0xFFF5A524)
                                else -> Color(0xFF7B8FF5)
                            }
                        )
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "已用 ${Money.format(shown)} / ${Money.format(limit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.66f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "可用 ${Money.format((limit - shown).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.66f)
                )
            }
        }
    }
}

@Composable
private fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
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
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RecentRow(
    txn: TransactionEntity,
    state: com.keepaccounts.ui.viewmodel.AccountDetailUiState,
    accountId: Long,
    liability: Boolean
) {
    val category = txn.categoryId?.let { state.categories[it] }
    val isAdjustment = txn.source == TxSource.ADJUSTMENT
    val isTransfer = txn.type == TxType.TRANSFER
    // 钱流向这个账户 = 正
    val incoming = when (txn.type) {
        TxType.INCOME, TxType.REFUND -> true
        TxType.TRANSFER -> txn.toAccountId == accountId
        else -> false
    }

    val title = when {
        isAdjustment -> "余额调整"
        !txn.merchant.isNullOrBlank() -> txn.merchant.orEmpty()
        !txn.note.isNullOrBlank() -> txn.note.orEmpty()
        isTransfer -> if (incoming) "还款 / 转入" else "转出"
        category != null -> category.name
        else -> "未分类"
    }
    val subtitle = listOfNotNull(
        Time.toLocal(txn.occurredAt).let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) },
        if (isAdjustment) "不计收支" else if (isTransfer) "转账" else category?.name
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterSquare(
            text = when {
                isAdjustment -> "调"
                isTransfer -> "还"
                else -> category?.name ?: "?"
            },
            colorHex = if (isAdjustment) "#A0A6B0" else (category?.color ?: "#6287D8"),
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
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (incoming) "+" else "-") + Money.format(abs(txn.amount)),
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            fontWeight = FontWeight.SemiBold,
            color = when {
                isAdjustment -> KaTheme.extra.transfer
                isTransfer -> if (incoming) KaTheme.extra.income else KaTheme.extra.expense
                incoming -> KaTheme.extra.income
                else -> KaTheme.extra.expense
            }
        )
    }
}
