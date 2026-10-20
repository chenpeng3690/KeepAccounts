package com.keepaccounts.ui.screen

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepaccounts.core.AppContainer
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.ScreenHeader
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.theme.KaTheme

/**
 * 尚未实现的 Tab 的占位页。
 *
 * 刻意写清「第几步补上什么」，这样每次真机跑起来都能对上进度，
 * 而不是看到一片空白猜是坏了还是没做。
 */
@Composable
private fun StepPlaceholder(
    title: String,
    step: Int,
    description: String,
    planned: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(title = title)

        KaCard {
            Text(
                text = "第 $step 步实现",
                style = MaterialTheme.typography.labelMedium,
                color = KaTheme.extra.warning,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
        }

        SectionLabel(text = "这一步会包含")
        KaCard {
            planned.forEach { item ->
                Text(
                    text = "·  $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun StatsScreen(container: AppContainer) {
    StepPlaceholder(
        title = "统计",
        step = 8,
        description = "按月看收支结余、分类占比与趋势。统计会排除「余额调整」与转账，只算真实收支。",
        planned = listOf(
            "分类占比环形图，可下钻到明细",
            "近 7 天 / 月度趋势折线",
            "日历热力图：每天花了多少一眼看清",
            "预算进度与超支提醒",
            "微信 / 支付宝账单 CSV 导入"
        )
    )
}

