package com.keepaccounts.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.core.AppContainer
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.ScreenHeader
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.util.Money

/**
 * 账单首页。
 *
 * 第 1 步只有汇总与数据自检：把数据库真的读出来显示，用来确认 Room + SQLCipher
 * 在真机上跑通（种子数据 26 个分类 + 1 个现金账户）。第 3 步接上真实账单列表。
 */
@Composable
fun LedgerScreen(container: AppContainer) {
    val expense by container.txnRepo.observeMonthExpense()
        .collectAsStateWithLifecycle(initialValue = 0L)
    val income by container.txnRepo.observeMonthIncome()
        .collectAsStateWithLifecycle(initialValue = 0L)
    val accounts by container.accountRepo.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by container.categoryRepo.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(title = "账单")

        KaCard {
            Text(
                text = "本月支出",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                MetricBlock(
                    label = "本月收入",
                    value = Money.format(income),
                    valueColor = KaTheme.extra.income
                )
                MetricBlock(
                    label = "本月结余",
                    value = Money.format(income - expense),
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        SectionLabel(text = "本地数据", trailing = "已加密")
        KaCard {
            InfoRow("分类", "${categories.size} 个")
            InfoRow("账户", "${accounts.size} 个")
            InfoRow("加密方式", "SQLCipher · 密钥由 Keystore 保护")
        }

        SectionLabel(text = "当前进度")
        KaCard {
            Text(
                text = "第 2 步 · 账户与资产",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "账户增删改、净资产、信用卡额度、转账、余额校准已经可用。" +
                    "接下来：记账与账单列表 → 回收站与备份 → 通知采集 → 解析引擎。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricBlock(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
