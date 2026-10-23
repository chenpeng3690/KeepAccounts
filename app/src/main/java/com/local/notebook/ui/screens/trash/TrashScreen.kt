package com.local.notebook.ui.screens.trash

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import com.local.notebook.data.local.TxnRow
import com.local.notebook.ui.components.CategoryTile
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.RetentionDialog
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.util.formatClock
import com.local.notebook.ui.util.formatMoney
import com.local.notebook.ui.util.formatShortDate
import com.local.notebook.ui.util.millisToLocalDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val rows by viewModel.deleted.collectAsStateWithLifecycle()
    val retention by viewModel.retentionDays.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var purgeTarget by remember { mutableStateOf<TxnRow?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showRetention by remember { mutableStateOf(false) }
    var clearInput by remember { mutableStateOf("") }

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
            RoundIconButton(
                icon = R.drawable.ic_chevron_left,
                contentDescription = "返回",
                onClick = onBack
            )
            Text(
                "回收站",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                "设置",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.brand,
                modifier = Modifier
                    .clickable { showRetention = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

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
                    "删除的账单先放这里，保留 $retention 天后自动彻底删除。期间随时可以恢复。",
                    fontSize = 11.sp,
                    color = p.ink2,
                    lineHeight = 17.sp
                )
            }
        }

        Text(
            "${rows.size} 条记录",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.ink3,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 2.dp)
        )

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(p.track),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_trash),
                        contentDescription = null,
                        tint = p.ink3,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    "回收站是空的",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.ink,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    "删除的账单会暂存在这里 $retention 天",
                    fontSize = 11.sp,
                    color = p.ink3,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            rows.forEach { row ->
                TrashCard(
                    row = row,
                    retentionDays = retention,
                    onRestore = {
                        scope.launch {
                            viewModel.restore(row.id)
                            snackbarHostState.showSnackbar("已恢复「${merchantOf(row)}」")
                        }
                    },
                    onPurge = { purgeTarget = row }
                )
                Spacer(Modifier.height(9.dp))
            }

            Text(
                "清空回收站",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.dangerText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clearInput = ""
                        showClearConfirm = true
                    }
                    .padding(vertical = 14.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showRetention) {
        RetentionDialog(
            current = retention,
            onSelect = viewModel::setRetention,
            onDismiss = { showRetention = false }
        )
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            containerColor = p.card,
            title = { Text("彻底删除这笔账单？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Text(
                    "删除后无法恢复，相关编辑记录也会一并清除。",
                    fontSize = 12.sp,
                    color = p.ink2,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    purgeTarget = null
                    scope.launch {
                        viewModel.purge(id)
                        snackbarHostState.showSnackbar("已彻底删除")
                    }
                }) { Text("彻底删除", color = p.dangerText) }
            },
            dismissButton = {
                TextButton(onClick = { purgeTarget = null }) { Text("取消", color = p.ink2) }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = p.card,
            title = { Text("清空回收站？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Column {
                    Text(
                        "所有账单将被永久删除且无法恢复。请输入「清空」确认。",
                        fontSize = 12.sp,
                        color = p.ink2,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = clearInput,
                        onValueChange = { clearInput = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        placeholder = { Text("清空", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = clearInput.trim() == "清空",
                    onClick = {
                        showClearConfirm = false
                        scope.launch {
                            viewModel.clearAll()
                            snackbarHostState.showSnackbar("回收站已清空")
                        }
                    }
                ) { Text("确认清空", color = p.dangerText) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消", color = p.ink2) }
            }
        )
    }
}

@Composable
private fun TrashCard(
    row: TxnRow,
    retentionDays: Int,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    val p = LedgerTheme.palette
    val tone = runCatching { LedgerTone.valueOf(row.categoryTone ?: "GRAY") }.getOrDefault(LedgerTone.GRAY)
    val expense = row.type == "EXPENSE"
    val remaining = remainingDays(row.deletedAt, retentionDays)
    LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(text = row.categoryIcon ?: "其", tone = tone)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    merchantOf(row),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.ink,
                    maxLines = 1
                )
                Text(
                    "${row.deletedAt?.let { "${formatShortDate(it)} ${formatClock(it)}" } ?: ""} · " +
                        "${row.categoryName ?: "未分类"} · ${row.accountName ?: "未知账户"}",
                    fontSize = 11.sp,
                    color = p.ink3,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (expense) "-" else "+") + formatMoney(row.amount),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (expense) p.expense else p.income
                )
                Text(
                    "还剩 $remaining 天",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining <= 1) p.ink3 else p.warnText,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (remaining <= 1) p.track else p.warnSoft)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(p.brandSoft)
                    .clickable(onClick = onRestore),
                contentAlignment = Alignment.Center
            ) {
                Text("恢复", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.brandSoftText)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(p.track)
                    .clickable(onClick = onPurge),
                contentAlignment = Alignment.Center
            ) {
                Text("彻底删除", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.dangerText)
            }
        }
    }
}

private fun merchantOf(row: TxnRow): String =
    row.merchant?.takeIf { it.isNotBlank() }
        ?: row.note?.takeIf { it.isNotBlank() }
        ?: row.categoryName
        ?: "未命名账单"

private fun remainingDays(deletedAt: Long?, retentionDays: Int): Int {
    if (deletedAt == null) return 0
    val deleted = millisToLocalDate(deletedAt)
    val passed = ChronoUnit.DAYS.between(deleted, LocalDate.now()).toInt()
    return (retentionDays - passed).coerceAtLeast(0)
}
