package com.local.notebook.ui.screens.logs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.formatClock
import com.local.notebook.ui.util.formatShortDate
import com.local.notebook.ui.util.millisToLocalDate
import java.time.LocalDate

@Composable
fun ParseLogScreen(
    onBack: () -> Unit,
    viewModel: ParseLogViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val filter by viewModel.currentFilter.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item {
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
                    "解析日志",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(30.dp))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LogFilter.entries.forEach { item ->
                    val selected = item == filter
                    Text(
                        item.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) p.selectedChipText else p.ink2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) p.selectedChipBg else p.card)
                            .clickable { viewModel.setFilter(item) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(p.card),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rules),
                            contentDescription = null,
                            tint = p.ink3,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        if (filter == LogFilter.ALL) "还没有解析记录" else "这个分类下暂无记录",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.ink,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "白名单应用的通知会在这里显示处理结果",
                        fontSize = 10.5.sp,
                        color = p.ink3,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                LogRow(log)
                HorizontalDivider(thickness = 1.dp, color = p.divider)
            }
        }
    }
}

@Composable
private fun LogRow(log: NotificationRawEntity) {
    val p = LedgerTheme.palette
    val today = LocalDate.now()
    val date = millisToLocalDate(log.receivedAt)
    val timeText = if (date == today) {
        formatClock(log.receivedAt)
    } else {
        "${formatShortDate(log.receivedAt)} ${formatClock(log.receivedAt)}"
    }
    val bodyFirst = log.text.orEmpty()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val title = log.title.orEmpty().trim()
    val headline = when {
        title.isNotEmpty() && bodyFirst.isNotEmpty() && title != bodyFirst -> "$title · $bodyFirst"
        title.isNotEmpty() -> title
        else -> bodyFirst.ifBlank { "（空通知）" }
    }
    val reason = log.rejectReason.orEmpty().ifBlank {
        when (log.matchResult) {
            "MATCHED" -> "已入账"
            "PENDING" -> "待确认"
            else -> "已拦截"
        }
    }
    val rejected = log.matchResult == "REJECTED"

    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 11.dp)) {
        Text(
            timeText,
            fontSize = 10.sp,
            color = p.ink3,
            modifier = Modifier
                .width(46.dp)
                .padding(top = 1.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${log.appLabel ?: log.packageName} · $headline",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                maxLines = 1
            )
            Text(
                reason,
                fontSize = 10.5.sp,
                color = if (rejected) p.warnText else p.ink3,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2
            )
        }
    }
}
