package com.keepaccounts.ui.screen.rules

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.RuleEntity
import com.keepaccounts.data.local.db.RuleState
import com.keepaccounts.ui.component.KaBadge
import com.keepaccounts.ui.component.KaBadgeStyle
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.viewmodel.RuleListViewModel

@Composable
fun RuleListScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onNewRule: () -> Unit
) {
    val vm = kaViewModel { RuleListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // nav：返回 + 标题 + 新建
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "解析规则",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .clickable(onClick = onNewRule),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "新建规则",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 内置规则
            SectionLabel(text = "内置规则", trailing = "不可删除")
            RuleGroup(state.rules.filter { it.builtin && it.packageName != null }, vm::toggle)

            // 我的规则
            SectionLabel(text = "我的规则")
            if (state.rules.none { !it.builtin }) {
                EmptyRules(onNewRule)
            } else {
                RuleGroup(state.rules.filter { !it.builtin }, vm::toggle)
            }

            // 兜底
            SectionLabel(text = "兜底")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    val generic = state.rules.firstOrNull { it.packageName == null }
                    if (generic != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LetterAvatar(text = "通", colorHex = "#6B7280", size = 36.dp)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "通用规则",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "命中后进入待确认，不会直接记账",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = generic.state != RuleState.DISABLED,
                                onCheckedChange = { vm.toggle(generic.id, generic.state == RuleState.ACTIVE) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "只有白名单里的应用通知会被读取",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(80.dp))
        }

        // 底部固定「新建规则」
        Surface(
            onClick = onNewRule,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "新建规则",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun RuleGroup(rules: List<RuleEntity>, onToggle: (Long, Boolean) -> Unit) {
    if (rules.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            rules.forEach { rule ->
                RuleRow(rule, onToggle)
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RuleEntity, onToggle: (Long, Boolean) -> Unit) {
    val (badgeText, badgeStyle) = when (rule.state) {
        RuleState.ACTIVE -> "已启用" to KaBadgeStyle.ON
        RuleState.OBSERVING -> "观察中" to KaBadgeStyle.WATCH
        else -> "已停用" to KaBadgeStyle.OFF
    }
    val stats = when {
        rule.state == RuleState.OBSERVING ->
            "观察中 · 已确认 ${rule.confirmedCount}/3 条"
        rule.hitCount == 0 && rule.lastHitAt != null ->
            "可能已失效，通知格式有变"
        rule.hitCount == 0 ->
            "近 7 天命中 0 条"
        else ->
            "近 7 天命中 ${rule.hitCount} 条"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(rule.id, rule.state == RuleState.ACTIVE) }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(text = rule.name.take(1), colorHex = "#3B5BDB", size = 36.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stats,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        KaBadge(badgeText, badgeStyle)
    }
}

@Composable
private fun EmptyRules(onNewRule: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Text(
            text = "还没有自定义规则。贴一条通知、点一下金额，就能给某个应用生成专属规则。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
