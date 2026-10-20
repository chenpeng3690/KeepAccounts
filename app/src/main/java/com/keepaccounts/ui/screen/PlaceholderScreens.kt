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
fun PendingScreen(container: AppContainer) {
    StepPlaceholder(
        title = "待确认",
        step = 6,
        description = "通用规则猜出来的账单不直接入账，先落在这里等你确认。空态时会显示最近 7 天的解析概况，用来验证过滤规则还在正常工作。",
        planned = listOf(
            "低置信度记录列表：原文与抽取结果对照",
            "确认入账 / 修改后入账 / 判为噪音 / 批量确认",
            "「以后这个应用都这样记」一键生成规则草稿",
            "空态：已自动入账与已拦截的统计 + 被拦通知摘要",
            "解析日志入口"
        )
    )
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

@Composable
fun SettingsScreen(container: AppContainer) {
    StepPlaceholder(
        title = "设置",
        step = 4,
        description = "通知使用权状态、白名单、解析规则、备份与回收站都在这里。通知使用权会常驻显示——服务被系统杀掉后，这个入口必须随时可查。",
        planned = listOf(
            "通知使用权状态与跳转授权",
            "回收站保留期：3 / 7 / 30 天",
            "手动备份与恢复（口令加密）",
            "自动备份：默认关闭，周期 1 / 3 / 7 / 30 天",
            "记账提示档位与静音时段",
            "深色模式跟随系统"
        )
    )
}
