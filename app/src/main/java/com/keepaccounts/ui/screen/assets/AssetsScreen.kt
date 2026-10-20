package com.keepaccounts.ui.screen.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.ui.AccountLabels
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.component.ScreenHeader
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.AssetsViewModel
import com.keepaccounts.util.Money
import kotlinx.coroutines.launch

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
        ScreenHeader(title = "资产") {
            IconButton(onClick = { showCreate = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加账户",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        NetWorthCard(
            netWorth = state.netWorth,
            totalAssets = state.totalAssets,
            totalLiabilities = state.totalLiabilities
        )

        val allAccounts = state.assetAccounts + state.liabilityAccounts
        if (state.loaded && allAccounts.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            EmptyAccountsCard { showCreate = true }
        }

        if (state.assetAccounts.isNotEmpty()) {
            SectionLabel(text = "资产", trailing = "${state.assetAccounts.size} 个账户")
            AccountGroup(state.assetAccounts, onOpenAccount)
        }

        if (state.liabilityAccounts.isNotEmpty()) {
            SectionLabel(text = "负债", trailing = "${state.liabilityAccounts.size} 个账户")
            AccountGroup(state.liabilityAccounts, onOpenAccount)
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
private fun NetWorthCard(netWorth: Long, totalAssets: Long, totalLiabilities: Long) {
    // 深色渐变卡在浅色和深色主题下都成立，所以不走 colorScheme，固定色值
    val brush = Brush.linearGradient(
        listOf(Color(0xFF2A3550), Color(0xFF3B4A78), Color(0xFF48539B))
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Text(
            text = "净资产",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.62f)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "¥ ${Money.format(netWorth)}",
            style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            NetMetric("总资产", Money.format(totalAssets), Modifier.weight(1f))
            NetMetric("总负债", Money.format(totalLiabilities), Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NetMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyAccountsCard(onCreate: () -> Unit) {
    KaCard {
        Text(
            text = "还没有账户",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
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

@Composable
private fun AccountGroup(accounts: List<AccountEntity>, onOpenAccount: (Long) -> Unit) {
    KaCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
        accounts.forEachIndexed { index, account ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            AccountRow(account = account, onClick = { onOpenAccount(account.id) })
        }
    }
}

@Composable
private fun AccountRow(account: AccountEntity, onClick: () -> Unit) {
    val liability = AccountLabels.isLiability(account.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(text = account.name, colorHex = account.color, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(AccountLabels.typeName(account.type))
                    if (!account.cardTail.isNullOrBlank()) append(" · 尾号 ${account.cardTail}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.format(account.currentBalance),
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                fontWeight = FontWeight.SemiBold,
                color = if (liability) KaTheme.extra.expense
                else MaterialTheme.colorScheme.onSurface
            )
            if (liability) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "欠款",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
