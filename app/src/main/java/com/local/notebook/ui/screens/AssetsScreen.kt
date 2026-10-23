package com.local.notebook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.ui.components.CategoryTile
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.ScreenHeader
import com.local.notebook.ui.components.SectionHeader
import com.local.notebook.ui.screens.assets.AssetAccountUi
import com.local.notebook.ui.screens.assets.AssetsViewModel
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.formatMoney
import kotlin.math.abs

@Composable
fun AssetsScreen(
    onAddAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    viewModel: AssetsViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(title = "资产") {
            RoundIconButton(
                icon = R.drawable.ic_plus,
                contentDescription = "添加账户",
                onClick = onAddAccount
            )
        }

        NetWorthCard(
            netWorth = state.netWorth,
            totalAssets = state.totalAssets,
            totalLiabilities = state.totalLiabilities,
            monthChange = state.monthChange,
            composition = state.composition
        )

        SectionHeader(
            title = "资产",
            trailing = "${state.assets.size} 个账户",
            modifier = Modifier.padding(top = 16.dp)
        )
        if (state.assets.isEmpty()) {
            LedgerCard(shape = RoundedCornerShape(15.dp)) {
                Text(
                    "还没有资产账户，点右上角 + 添加",
                    fontSize = 11.sp,
                    color = LedgerTheme.palette.ink3,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            AccountCard(state.assets, onOpenAccount)
        }

        SectionHeader(
            title = "负债",
            trailing = "${state.liabilities.size} 个账户",
            modifier = Modifier.padding(top = 16.dp)
        )
        if (state.liabilities.isEmpty()) {
            LedgerCard(shape = RoundedCornerShape(15.dp)) {
                Text(
                    "没有负债账户",
                    fontSize = 11.sp,
                    color = LedgerTheme.palette.ink3,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            AccountCard(state.liabilities, onOpenAccount)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NetWorthCard(
    netWorth: Long,
    totalAssets: Long,
    totalLiabilities: Long,
    monthChange: Long,
    composition: List<Float>
) {
    val p = LedgerTheme.palette
    val changeSign = if (monthChange >= 0) "+" else "-"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(p.netGradient))
            .padding(18.dp)
    ) {
        Text("净资产", fontSize = 11.sp, color = Color.White.copy(alpha = 0.62f), letterSpacing = 0.4.sp)
        Text(
            "¥ ${formatMoney(netWorth)}",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
            color = Color.White,
            modifier = Modifier.padding(top = 5.dp)
        )
        Text(
            "本月变动 $changeSign${formatMoney(abs(monthChange))}",
            fontSize = 10.5.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.13f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp)
        ) {
            NetStat("总资产", formatMoney(totalAssets), Modifier.weight(1f))
            NetStat("总负债", formatMoney(totalLiabilities), Modifier.weight(1f))
            NetStat("本月变动", "$changeSign${formatMoney(abs(monthChange))}", Modifier.weight(1f))
        }
        val visible = composition.filter { it > 0.0001f }
        if (visible.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                visible.forEachIndexed { index, weight ->
                    Box(
                        Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(p.assetStack[index.coerceAtMost(p.assetStack.size - 1)])
                    )
                }
            }
        }
    }
}

@Composable
private fun NetStat(k: String, v: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(k, fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.55f))
        Text(
            v,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun AccountCard(accounts: List<AssetAccountUi>, onOpen: (Long) -> Unit) {
    val p = LedgerTheme.palette
    LedgerCard(
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
    ) {
        accounts.forEachIndexed { index, account ->
            if (index > 0) {
                HorizontalDivider(thickness = 1.dp, color = p.divider)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(account.id) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryTile(text = account.char, tone = account.tone)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.ink,
                        maxLines = 1
                    )
                    Text(
                        account.meta,
                        fontSize = 11.sp,
                        color = p.ink3,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (account.liability) "-" else "") +
                            formatMoney(abs(account.balance)),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        color = if (account.liability) p.expense else p.ink
                    )
                    if (account.liability) {
                        Text(
                            "欠款",
                            fontSize = 9.5.sp,
                            color = p.ink3,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
