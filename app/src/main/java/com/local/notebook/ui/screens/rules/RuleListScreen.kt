package com.local.notebook.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.ui.components.BadgeKind
import com.local.notebook.ui.components.CategoryTile
import com.local.notebook.ui.components.LedgerBadge
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.LedgerSwitch
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.SectionHeader
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone

@Composable
fun RuleListScreen(
    onBack: () -> Unit,
    onNewRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    viewModel: RuleListViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionRule by remember { mutableStateOf<RuleRowUi?>(null) }
    var deleteTarget by remember { mutableStateOf<RuleRowUi?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(p.bg),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
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
                        "解析规则",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.ink,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    RoundIconButton(
                        icon = R.drawable.ic_plus,
                        contentDescription = "新建规则",
                        onClick = onNewRule
                    )
                }
            }

            item {
                SectionHeader(
                    title = "内置规则",
                    trailing = "不可删除",
                    modifier = Modifier.padding(top = 0.dp)
                )
            }
            item {
                LedgerCard(
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    state.builtin.forEachIndexed { index, rule ->
                        if (index > 0) {
                            HorizontalDivider(thickness = 1.dp, color = p.divider)
                        }
                        RuleRow(
                            rule = rule,
                            onOpen = { onEditRule(rule.id) },
                            onMore = { actionRule = rule }
                        )
                    }
                }
            }

            item { SectionHeader(title = "我的规则", modifier = Modifier.padding(top = 16.dp)) }
            item {
                LedgerCard(
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    if (state.user.isEmpty()) {
                        Text(
                            "还没有自定义规则，可以点下方「新建规则」，或从待确认记录一键生成。",
                            fontSize = 11.sp,
                            color = p.ink3,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        state.user.forEachIndexed { index, rule ->
                            if (index > 0) {
                                HorizontalDivider(thickness = 1.dp, color = p.divider)
                            }
                            RuleRow(
                                rule = rule,
                                onOpen = { onEditRule(rule.id) },
                                onMore = { actionRule = rule }
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = "兜底", modifier = Modifier.padding(top = 16.dp)) }
            item {
                LedgerCard(
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryTile(text = "通", tone = LedgerTone.GRAY)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "通用规则",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = p.ink
                            )
                            Text(
                                "命中后进入待确认，不会直接记账",
                                fontSize = 9.5.sp,
                                color = p.ink3,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        LedgerSwitch(
                            checked = state.fallbackEnabled,
                            onCheckedChange = viewModel::setBuiltinFallback
                        )
                    }
                }
            }

            item {
                Text(
                    "只有白名单里的应用通知会被读取",
                    fontSize = 10.5.sp,
                    color = p.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, p.bg.copy(alpha = 0.96f), p.bg)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.brand)
                    .clickable(onClick = onNewRule),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "新建规则",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }

    actionRule?.let { rule ->
        RuleActionDialog(
            rule = rule,
            onDismiss = { actionRule = null },
            onEdit = {
                actionRule = null
                onEditRule(rule.id)
            },
            onEnable = {
                viewModel.enableRule(rule)
                actionRule = null
            },
            onToggle = {
                viewModel.toggleState(rule)
                actionRule = null
            },
            onDuplicate = {
                viewModel.duplicate(rule)
                actionRule = null
            },
            onMoveUp = {
                viewModel.move(rule, 10)
                actionRule = null
            },
            onMoveDown = {
                viewModel.move(rule, -10)
                actionRule = null
            },
            onDelete = {
                actionRule = null
                deleteTarget = rule
            }
        )
    }

    deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = p.card,
            title = {
                Text("删除规则「${rule.name}」？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
            },
            text = {
                Text(
                    "删除后，该应用的通知将不再按这条规则解析。已生成的账单不会受影响。",
                    fontSize = 12.sp,
                    color = p.ink2,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(rule)
                    deleteTarget = null
                }) { Text("删除", color = p.dangerText) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = p.ink2) }
            }
        )
    }
}

@Composable
private fun RuleRow(
    rule: RuleRowUi,
    onOpen: () -> Unit,
    onMore: () -> Unit
) {
    val p = LedgerTheme.palette
    val badge = when {
        rule.state == "DISABLED" -> "已停用" to BadgeKind.OFF
        rule.healthWarning -> "待更新" to BadgeKind.WATCH
        rule.state == "OBSERVING" -> "观察中" to BadgeKind.WATCH
        else -> "已启用" to BadgeKind.ACTIVE
    }
    val stats = when {
        rule.healthWarning -> "可能已失效，通知格式有变"
        rule.state == "OBSERVING" && rule.statsRejected > 0 ->
            "观察中 · 已确认 ${rule.statsConfirmed} 条 · 噪音 ${rule.statsRejected} 条"
        rule.state == "OBSERVING" -> "观察中 · 已确认 ${rule.statsConfirmed}/3 条"
        rule.state == "DISABLED" -> "已停用 · 近 7 天命中 ${rule.statsHits7d} 条"
        else -> "近 7 天命中 ${rule.statsHits7d} 条"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryTile(text = rule.appChar, tone = rule.tone)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rule.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                maxLines = 1
            )
            Text(
                stats,
                fontSize = 9.5.sp,
                color = if (rule.healthWarning) p.warnText else p.ink3,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 1
            )
        }
        LedgerBadge(text = badge.first, kind = badge.second)
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_more),
            contentDescription = "更多操作",
            tint = p.ink3,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onMore)
        )
    }
}

@Composable
private fun RuleActionDialog(
    rule: RuleRowUi,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onEnable: () -> Unit,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = {
            Text(rule.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
        },
        text = {
            Column {
                if (rule.readyToEnable) {
                    ActionRow("启用自动记账（已确认 3 条）", p.brand, onEnable)
                }
                ActionRow("编辑规则", p.ink, onEdit)
                ActionRow(
                    if (rule.state == "DISABLED") "启用规则" else "停用规则",
                    p.ink,
                    onToggle
                )
                ActionRow("复制规则", p.ink, onDuplicate)
                ActionRow("提高优先级", p.ink, onMoveUp)
                ActionRow("降低优先级", p.ink, onMoveDown)
                if (!rule.isBuiltin) {
                    ActionRow("删除规则", p.dangerText, onDelete)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = p.ink2) }
        }
    )
}

@Composable
private fun ActionRow(text: String, color: Color, onClick: () -> Unit) {
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
