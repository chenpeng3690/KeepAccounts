package com.keepaccounts.ui.screen.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.keepaccounts.ui.AccountLabels
import com.keepaccounts.ui.component.KaIconButton
import com.keepaccounts.ui.component.LetterSquare
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.theme.toColor
import com.keepaccounts.ui.viewmodel.AssetsViewModel
import com.keepaccounts.util.Money
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 资产总览。对齐 docs/ui-preview.html 第 04 屏：
 * 深色渐变净资产卡（含构成条）→ 资产分组 → 负债分组。
 */
@Composable
fun AssetsScreen(onOpenAccount: (Long) -> Unit) {
    val vm = kaViewModel { AssetsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }

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
                text = "资产",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            KaIconButton(Icons.Filled.Add, "添加账户") { showCreate = true }
        }

        NetWorthCard(
            netWorth = state.netWorth,
            totalAssets = state.totalAssets,
            totalLiabilities = state.totalLiabilities,
            monthExpense = state.monthExpense,
            monthIncome = state.monthIncome,
            assetAccounts = state.assetAccounts
        )

        val allAccounts = state.assetAccounts + state.liabilityAccounts
        if (state.loaded && allAccounts.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            EmptyAccountsCard { showCreate = true }
        }

        if (state.assetAccounts.isNotEmpty()) {
            GroupLabel("资产", "${state.assetAccounts.size} 个账户")
            AccountCard(state.assetAccounts, onOpenAccount, liability = false)
        }

        if (state.liabilityAccounts.isNotEmpty()) {
            GroupLabel("负债", "${state.liabilityAccounts.size} 个账户")
            AccountCard(state.liabilityAccounts, onOpenAccount, liability = true)
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showCreate) {
        AccountFormSheet(
            account = null,
            existingNames = (state.assetAccounts + state.liabilityAccounts).map { it.name },
            onDismiss = { showCreate = false },
            onSave = { data ->
                scope.launch {
                    vm.createAccount(
                        name = data.name,
                        type = data.type,
                        balanceSigned = data.balanceSigned,
                        creditLimit = data.creditLimit,
                        billDay = data.billDay,
                        repayDay = data.repayDay,
                        includeInNetWorth = data.includeInNetWorth
                    )
                    showCreate = false
                }
            }
        )
    }
}

@Composable
private fun NetWorthCard(
    netWorth: Long,
    totalAssets: Long,
    totalLiabilities: Long,
    monthExpense: Long,
    monthIncome: Long,
    assetAccounts: List<AccountEntity>
) {
    val brush = Brush.linearGradient(
        0f to Color(0xFF2A3550),
        0.52f to Color(0xFF3B4A78),
        1f to Color(0xFF48539B)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .padding(18.dp)
    ) {
        Text(
            text = "净资产",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.62f)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "¥ ${Money.format(netWorth)}",
            style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        val delta = monthIncome - monthExpense
        Text(
            text = "本月净变动 ${if (delta >= 0) "+" else "-"}¥${Money.format(abs(delta))}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.13f))
        )
        Spacer(Modifier.height(13.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            NetCell("总资产", Money.format(totalAssets), Modifier.weight(1f))
            NetCell("总负债", Money.format(totalLiabilities), Modifier.weight(1f))
            NetCell("本月支出", Money.format(monthExpense), Modifier.weight(1f))
        }

        // 构成条：按各资产账户余额占比上色，颜色取自账户自身色值
        val positives = assetAccounts.filter { it.currentBalance > 0 }
        val sum = positives.sumOf { it.currentBalance }
        if (sum > 0) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                positives.forEach { account ->
                    val ratio = (account.currentBalance.toFloat() / sum.toFloat()).coerceAtLeast(0.02f)
                    Box(
                        modifier = Modifier
                            .weight(ratio)
                            .fillMaxSize()
                            .background(
                                runCatching { account.color.toColor() }
                                    .getOrElse { Color.White }
                                    .copy(alpha = 0.85f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun NetCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun GroupLabel(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun AccountCard(
    accounts: List<AccountEntity>,
    onOpenAccount: (Long) -> Unit,
    liability: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
            accounts.forEachIndexed { index, account ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                AccountRow(account, liability) { onOpenAccount(account.id) }
            }
        }
    }
}

@Composable
private fun AccountRow(account: AccountEntity, liability: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterSquare(text = account.name, colorHex = account.color, size = 36.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitleOf(account, liability),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.format(account.currentBalance),
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                fontWeight = FontWeight.SemiBold,
                color = if (liability) KaTheme.extra.expense else MaterialTheme.colorScheme.onSurface
            )
            if (liability) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "欠款",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 副标题按类型给不同信息：储蓄卡给尾号，信用卡给可用额度与还款日 */
private fun subtitleOf(account: AccountEntity, liability: Boolean): String {
    if (liability) {
        val used = if (account.currentBalance < 0) -account.currentBalance else 0L
        val limit = account.creditLimit
        return buildList {
            if (limit != null && limit > 0) {
                add("可用额度 ${Money.format((limit - used).coerceAtLeast(0))}")
            }
            account.repayDay?.let { add("$it 日还款") }
            if (isEmpty()) add(AccountLabels.typeName(account.type))
        }.joinToString(" · ")
    }
    return buildList {
        account.cardTail?.takeIf { it.isNotBlank() }?.let { add("尾号 $it") }
        if (isEmpty()) add(AccountLabels.typeName(account.type))
    }.joinToString(" · ")
}

@Composable
private fun EmptyAccountsCard(onCreate: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "还没有账户",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "先建一个「现金」或「储蓄卡」，记账才有地方落。" +
                    "信用卡、花呗这类账户余额为负，会自动算进负债。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "添加账户",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
