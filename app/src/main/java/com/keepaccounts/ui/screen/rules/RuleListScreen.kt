package com.keepaccounts.ui.screen.rules

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.viewmodel.RuleListViewModel
import com.keepaccounts.util.Time

@Composable
fun RuleListScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onNewRule: () -> Unit
) {
    val vm = kaViewModel { RuleListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "解析规则",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onOpenLog,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "解析日志",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        SectionLabel(text = "内置规则（开箱即用，可停用不可删除）")
        state.rules.filter { it.builtin }.forEach { rule ->
            RuleCard(
                rule = rule,
                onToggle = { vm.toggle(rule.id, rule.state == RuleState.ACTIVE) }
            )
            Spacer(Modifier.height(10.dp))
        }

        SectionLabel(text = "自定义规则")
        if (state.rules.none { !it.builtin }) {
            KaCard {
                Text(
                    text = "还没有自定义规则。贴一条通知、点一下金额，就能给某个应用生成专属规则，让自动记账越来越准。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onNewRule,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
        } else {
            state.rules.filter { !it.builtin }.forEach { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { vm.toggle(rule.id, rule.state == RuleState.ACTIVE) }
                )
                Spacer(Modifier.height(10.dp))
            }
            Surface(
                onClick = onNewRule,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ 新建规则",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RuleCard(rule: RuleEntity, onToggle: () -> Unit) {
    val (badgeText, badgeColor) = when (rule.state) {
        RuleState.ACTIVE -> "已启用" to KaTheme.extra.income
        RuleState.OBSERVING -> "观察中" to KaTheme.extra.warning
        else -> "已停用" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.14f)) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(rule.packageName ?: "通用兜底 · 所有应用")
                        append(" · 命中 ")
                        append(rule.hitCount)
                        append(" 次")
                        rule.lastHitAt?.let {
                            append(" · 上次 ")
                            append(Time.formatDateTime(it))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = rule.state != RuleState.DISABLED,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
