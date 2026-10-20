package com.keepaccounts.ui.screen.parselog

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.ParseStatus
import com.keepaccounts.data.local.db.RawEventEntity
import com.keepaccounts.ui.component.KaChips
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.viewmodel.LogFilter
import com.keepaccounts.ui.viewmodel.ParseLogViewModel
import com.keepaccounts.util.Time

@Composable
fun ParseLogScreen(onBack: () -> Unit) {
    val vm = kaViewModel { ParseLogViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "解析日志",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        KaChips(
            options = LogFilter.entries.map { it.label },
            selectedIndex = LogFilter.entries.indexOf(state.filter),
            onSelect = { vm.setFilter(LogFilter.entries[it]) }
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.items, key = { it.id }) { event ->
                LogCard(event)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LogCard(event: RawEventEntity) {
    val (label, color) = when (event.parseStatus) {
        ParseStatus.MATCHED -> "已记账" to KaTheme.extra.income
        ParseStatus.PENDING -> "待确认" to KaTheme.extra.warning
        ParseStatus.REJECTED -> "被拦截" to KaTheme.extra.expense
        ParseStatus.UNMATCHED -> "未命中" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "未解析" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.appLabel ?: event.packageName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = event.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        event.rejectReason?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "原因：$it",
                style = MaterialTheme.typography.labelSmall,
                color = if (event.parseStatus == ParseStatus.REJECTED) KaTheme.extra.expense
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = Time.formatDateTime(event.receivedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
