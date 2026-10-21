package com.keepaccounts.ui.screen.trash

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.TrashItem
import com.keepaccounts.ui.viewmodel.TrashUiState
import com.keepaccounts.ui.viewmodel.TrashViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val vm = kaViewModel { TrashViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var confirmPurge by remember { mutableStateOf<TrashItem?>(null) }
    var confirmPurgeAll by remember { mutableStateOf(false) }
    var showRetention by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "回收站",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showRetention = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KaTheme.extra.pendingSurface)
                .padding(12.dp)
        ) {
            Text(
                text = "删除的账单先放这里，${state.retentionDays} 天后自动彻底删除。期间随时可以恢复。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 19.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "回收站是空的",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.items, key = { it.txn.id }) { item ->
                    TrashCard(
                        item = item,
                        state = state,
                        onRestore = { scope.launch { vm.restore(item.txn.id) } },
                        onPurge = { confirmPurge = item }
                    )
                }
                item {
                    Text(
                        text = "清空回收站",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmPurgeAll = true }
                            .padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    confirmPurge?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("彻底删除？") },
            text = { Text("这笔账单将被永久删除，不能恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.purge(item.txn.id) }
                    confirmPurge = null
                }) {
                    Text("彻底删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = null }) { Text("取消") }
            }
        )
    }

    if (confirmPurgeAll) {
        AlertDialog(
            onDismissRequest = { confirmPurgeAll = false },
            title = { Text("清空回收站？") },
            text = { Text("${state.items.size} 笔账单将被永久删除，不能恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.purgeAll() }
                    confirmPurgeAll = false
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurgeAll = false }) { Text("取消") }
            }
        )
    }

    if (showRetention) {
        AlertDialog(
            onDismissRequest = { showRetention = false },
            title = { Text("保留期") },
            text = {
                Column {
                    Text(
                        text = "改动后，已超过新期限的记录会立即清理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 7, 30).forEach { days ->
                            val selected = state.retentionDays == days
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        scope.launch {
                                            vm.setRetention(days)
                                            showRetention = false
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "$days 天",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetention = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun TrashCard(
    item: TrashItem,
    state: TrashUiState,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    val txn = item.txn
    val category = txn.categoryId?.let { state.categories[it] }
    val account = state.accounts[txn.accountId]
    val urgent = item.daysLeft <= 2
    val isAdjustment = txn.source == TxSource.ADJUSTMENT
    val amountColor = when {
        isAdjustment -> MaterialTheme.colorScheme.onSurfaceVariant
        txn.type == TxType.INCOME -> KaTheme.extra.income
        else -> KaTheme.extra.expense
    }
    val sign = when {
        isAdjustment -> ""
        txn.type == TxType.INCOME -> "+"
        else -> "-"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterAvatar(
                text = category?.name ?: "账",
                colorHex = category?.color ?: "#A0A6B0",
                size = 34.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = txn.merchant ?: category?.name ?: "未分类",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(
                        Time.formatMonthDay(txn.occurredAt),
                        account?.name
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$sign${Money.format(txn.amount)}",
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (urgent) KaTheme.extra.pendingSurface
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "还剩 ${item.daysLeft} 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (urgent) KaTheme.extra.warning
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onRestore)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "恢复",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPurge)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "彻底删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
