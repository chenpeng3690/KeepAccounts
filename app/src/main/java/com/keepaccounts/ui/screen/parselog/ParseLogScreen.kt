package com.keepaccounts.ui.screen.parselog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.ParseStatus
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.ui.component.FilterBar
import com.keepaccounts.ui.component.KaIconButton
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.viewmodel.LogFilter
import com.keepaccounts.ui.viewmodel.ParseLogViewModel
import com.keepaccounts.util.Time

@Composable
fun ParseLogScreen(onBack: () -> Unit) {
    val vm = kaViewModel { ParseLogViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showAppFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
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
                text = "解析日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            KaIconButton(Icons.Outlined.FilterList, "按应用筛选") { showAppFilter = true }
        }

        FilterBar(
            options = LogFilter.entries.map { it.label },
            selectedIndex = LogFilter.entries.indexOf(state.filter),
            onSelect = { vm.setFilter(LogFilter.entries[it]) }
        )

        Spacer(Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.items, key = { it.id }) { event ->
                LogRow(event)
                Spacer(Modifier.height(1.dp))
            }
        }
    }

    if (showAppFilter) {
        AppFilterSheet(
            apps = state.apps,
            current = state.appFilter,
            onSelect = { app ->
                vm.setAppFilter(app)
                showAppFilter = false
            },
            onDismiss = { showAppFilter = false }
        )
    }
}

@Composable
private fun AppFilterSheet(
    apps: List<String>,
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    KaSheet(title = "按应用筛选", onDismiss = onDismiss) {
        // 全部应用
        AppRow("全部应用", current == null) { onSelect(null) }
        apps.forEach { app ->
            AppRow(app, current == app) { onSelect(app) }
        }
    }
}

@Composable
private fun AppRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LogRow(event: RawEventEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp)
    ) {
        Text(
            text = Time.formatTime(event.receivedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(40.dp)
                .padding(top = 2.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "${event.appLabel ?: event.packageName} · ${event.title ?: event.text}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLine(event)
            }
        }
    }
}

@Composable
private fun StatusLine(event: RawEventEntity) {
    val emColor = Color(0xFFA9640A)
    when (event.parseStatus) {
        ParseStatus.MATCHED -> Text(
            text = "已入账",
            style = MaterialTheme.typography.labelSmall,
            color = KaTheme.extra.income
        )
        ParseStatus.PENDING -> Text(
            text = "待确认",
            style = MaterialTheme.typography.labelSmall,
            color = KaTheme.extra.warning
        )
        ParseStatus.REJECTED -> {
            Text(
                text = "已拦截 → ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = event.rejectReason ?: "被拦截",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = emColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ParseStatus.UNMATCHED -> Text(
            text = "未命中规则",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Text(
            text = "未解析",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
