package com.local.notebook.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.PendingRow
import com.local.notebook.data.local.RuleEntity
import com.local.notebook.ui.components.BadgeKind
import com.local.notebook.ui.components.CategoryTile
import com.local.notebook.ui.components.LedgerBadge
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.MiniAction
import com.local.notebook.ui.screens.pending.PendingOverview
import com.local.notebook.ui.screens.pending.PendingViewModel
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.util.formatClock
import com.local.notebook.ui.util.formatMoney
import com.local.notebook.ui.util.formatShortDate
import com.local.notebook.ui.util.millisToLocalDate
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PendingScreen(
    onOpenLogs: () -> Unit,
    onOpenRules: () -> Unit,
    onEditPending: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: PendingViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val readyRule by viewModel.ruleReady.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showBatch by remember { mutableStateOf(false) }

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
            Text("待确认", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink)
            if (state.items.isNotEmpty()) {
                Text(
                    state.items.size.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.warn,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.items.isNotEmpty()) {
                Text(
                    "批量处理",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.brand,
                    modifier = Modifier
                        .clickable { showBatch = true }
                        .padding(6.dp)
                )
            }
        }

        if (state.items.isNotEmpty()) {
            LedgerCard(
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)
            ) {
                Row {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = null,
                        tint = p.warn,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "低置信度的通知没直接入账，确认后才会进入账单。",
                        fontSize = 11.sp,
                        color = p.ink2,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            state.items.forEach { row ->
                PendingCard(
                    row = row,
                    onReject = {
                        scope.launch {
                            viewModel.reject(row.id)
                            snackbarHostState.showSnackbar("已判为噪音")
                        }
                    },
                    onEdit = { onEditPending(row.id) },
                    onConfirm = {
                        scope.launch {
                            viewModel.confirm(row.id)
                            snackbarHostState.showSnackbar("已确认入账")
                        }
                    }
                )
                Spacer(Modifier.height(9.dp))
            }

            Text(
                "规则不准？去管理解析规则 ›",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.brand,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRules)
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center
            )
            Text(
                "查看全部解析日志 ›",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.brand,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLogs)
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        } else {
            EmptyPending(
                overview = state.overview,
                onOpenLogs = onOpenLogs,
                onOpenRules = onOpenRules
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showBatch) {
        AlertDialog(
            onDismissRequest = { showBatch = false },
            containerColor = p.card,
            title = {
                Text("批量处理", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
            },
            text = {
                Text(
                    "共 ${state.items.size} 条待确认记录。可以选择全部确认入账，或全部判为噪音。",
                    fontSize = 12.sp,
                    color = p.ink2,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatch = false
                    scope.launch {
                        viewModel.confirmAll()
                        snackbarHostState.showSnackbar("已批量确认入账")
                    }
                }) { Text("全部确认入账", color = p.brand) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBatch = false
                    scope.launch {
                        viewModel.rejectAll()
                        snackbarHostState.showSnackbar("已批量判为噪音")
                    }
                }) { Text("全部判为噪音", color = p.dangerText) }
            }
        )
    }

    readyRule?.let { rule ->
        RuleReadyDialog(
            rule = rule,
            onEnable = { viewModel.enableRule(rule.id) },
            onDismiss = viewModel::dismissRuleOffer
        )
    }
}

@Composable
private fun PendingCard(
    row: PendingRow,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: () -> Unit
) {
    val p = LedgerTheme.palette
    val appLabel = row.appLabel ?: row.packageName ?: "未知应用"
    val tone = toneFor(row.packageName, appLabel)
    val timestamp = row.rawReceivedAt ?: row.occurredAt
    val timeText = formatPendingTime(timestamp)
    val badgeText = when {
        row.ruleId == null -> "通用规则"
        row.ruleState == "OBSERVING" -> "规则观察中"
        else -> row.ruleName ?: "规则命中"
    }
    val badgeKind = when {
        row.ruleId == null -> BadgeKind.WATCH
        row.ruleState == "OBSERVING" -> BadgeKind.INFO
        else -> BadgeKind.ACTIVE
    }
    val rawText = row.rawText?.takeIf { it.isNotBlank() }
        ?: row.rawTitle?.takeIf { it.isNotBlank() }
        ?: "（无原文）"
    val direction = when (row.type) {
        "INCOME" -> "收入"
        "TRANSFER" -> "转账"
        else -> "支出"
    }

    LedgerCard(
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(
                text = appLabel.take(1),
                tone = tone,
                size = 30,
                corner = 10,
                fontSize = 10.sp
            )
            Spacer(Modifier.width(8.dp))
            Text("$appLabel · $timeText", fontSize = 10.5.sp, color = p.ink3)
            Spacer(Modifier.weight(1f))
            LedgerBadge(text = badgeText, kind = badgeKind)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(p.cardAlt)
                .padding(horizontal = 9.dp, vertical = 8.dp)
        ) {
            Text(rawText, fontSize = 11.sp, color = p.ink2, lineHeight = 16.sp, maxLines = 3)
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExtractChip("金额 ${formatMoney(row.amount)}", p.successSoft, p.successText)
            if (row.merchant != null) {
                ExtractChip("商户 ${row.merchant}", p.successSoft, p.successText)
            } else {
                ExtractChip("商户 未识别", p.warnSoft, p.warnText)
            }
            ExtractChip(direction, p.track, p.ink2)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MiniAction("判为噪音", modifier = Modifier.weight(1f), onClick = onReject)
            MiniAction("修改", modifier = Modifier.weight(1f), onClick = onEdit)
            MiniAction("确认入账", modifier = Modifier.weight(1f), primary = true, onClick = onConfirm)
        }
    }
}

@Composable
private fun ExtractChip(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyPending(
    overview: PendingOverview,
    onOpenLogs: () -> Unit,
    onOpenRules: () -> Unit
) {
    val p = LedgerTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(p.successSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = p.income,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            "都处理完了",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.ink,
            modifier = Modifier.padding(top = 11.dp)
        )
        Text(
            "自动记账正常工作中",
            fontSize = 11.sp,
            color = p.ink3,
            modifier = Modifier.padding(top = 4.dp)
        )

        LedgerCard(
            modifier = Modifier.padding(top = 14.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
        ) {
            Text(
                "最近 7 天解析情况",
                fontSize = 10.5.sp,
                color = p.ink3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverviewCell(overview.autoPosted.toString(), "自动入账", p.ink, Modifier.weight(1f))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(p.divider)
                )
                OverviewCell(overview.blocked.toString(), "已拦噪音", p.ink3, Modifier.weight(1f))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(p.divider)
                )
                OverviewCell(overview.pending.toString(), "待确认", p.income, Modifier.weight(1f))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "最近被拦下的通知",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink3,
                modifier = Modifier.weight(1f)
            )
            Text(
                "去管理规则 ›",
                fontSize = 11.sp,
                color = p.brand,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onOpenRules)
            )
        }

        if (overview.rejected.isEmpty()) {
            LedgerCard(
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    "最近 7 天没有被拦下的通知",
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
                overview.rejected.forEachIndexed { index, raw ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 1.dp, color = p.divider)
                    }
                    RejectedRow(raw)
                }
            }
        }

        Text(
            "查看全部解析日志 ›",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.brand,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenLogs)
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OverviewCell(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val p = LedgerTheme.palette
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = color
        )
        Text(label, fontSize = 11.sp, color = p.ink3, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RejectedRow(raw: NotificationRawEntity) {
    val p = LedgerTheme.palette
    val appLabel = raw.appLabel ?: raw.packageName
    val headline = raw.title?.takeIf { it.isNotBlank() }
        ?: raw.text.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }
        ?: "（无原文）"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryTile(
            text = appLabel.take(1),
            tone = toneFor(raw.packageName, appLabel),
            size = 36,
            corner = 12
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                headline,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = p.ink,
                maxLines = 1
            )
            Text(
                raw.rejectReason.orEmpty().removePrefix("已拦截 → "),
                fontSize = 10.5.sp,
                color = p.ink3,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            relativeDay(raw.receivedAt),
            fontSize = 10.5.sp,
            color = p.ink3
        )
    }
}

@Composable
private fun RuleReadyDialog(
    rule: RuleEntity,
    onEnable: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = {
            Text("这条规则看起来可以了", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
        },
        text = {
            Text(
                "「${rule.name}」已累计确认 ${rule.statsConfirmed} 条，且没有误判。是否启用自动记账？",
                fontSize = 12.sp,
                color = p.ink2,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onEnable) { Text("启用自动记账", color = p.brand) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("再观察一下", color = p.ink2) }
        }
    )
}

private fun toneFor(packageName: String?, label: String): LedgerTone {
    return when {
        packageName == "com.tencent.mm" -> LedgerTone.GREEN
        packageName == "com.eg.android.AlipayGphone" -> LedgerTone.BLUE
        label.contains("抖音") || label.contains("快手") -> LedgerTone.GRAY
        label.contains("拼多多") -> LedgerTone.RED
        label.contains("美团") || label.contains("饿了么") -> LedgerTone.ORANGE
        else -> LedgerTone.entries[(packageName ?: label).hashCode().mod(LedgerTone.entries.size)]
    }
}

private fun formatPendingTime(millis: Long): String {
    val date = millisToLocalDate(millis)
    return if (date == LocalDate.now()) {
        formatClock(millis)
    } else {
        "${formatShortDate(millis)} ${formatClock(millis)}"
    }
}

private fun relativeDay(millis: Long): String {
    val date = millisToLocalDate(millis)
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> formatShortDate(millis)
    }
}
