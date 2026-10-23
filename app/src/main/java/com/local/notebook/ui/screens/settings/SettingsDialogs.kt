package com.local.notebook.ui.screens.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.local.notebook.R
import com.local.notebook.data.backup.BackupPreview
import com.local.notebook.data.importer.CsvImportPreview
import com.local.notebook.data.importer.ImportReport
import com.local.notebook.data.importer.ImportedRow
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.formatClock
import com.local.notebook.ui.util.formatMoney
import com.local.notebook.ui.util.formatShortDate
import com.local.notebook.ui.util.millisToLocalDate
import java.time.LocalDate

@Composable
fun SettingsMessageDialog(message: SettingsMessage, onDismiss: () -> Unit) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = {
            Text(
                if (message.error) "操作失败" else "提示",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (message.error) p.dangerText else p.ink
            )
        },
        text = { Text(message.text, fontSize = 12.sp, color = p.ink2, lineHeight = 18.sp) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了", color = p.brand) }
        }
    )
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (label == current || value == current) p.brand else p.ink,
                            fontWeight = if (value == current) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (value == current) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = p.brand,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = p.ink2) }
        }
    )
}

@Composable
fun RestorePreviewDialog(
    preview: BackupPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("恢复备份", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column {
                Text(
                    "备份时间：${formatShortDate(preview.createdAt)} ${formatClock(preview.createdAt)}",
                    fontSize = 11.5.sp,
                    color = p.ink2
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "包含 ${preview.txnCount} 条账单、${preview.accountCount} 个账户、${preview.ruleCount} 条规则",
                    fontSize = 11.5.sp,
                    color = p.ink2
                )
                Text(
                    "应用版本 ${preview.appVersion} · 数据版本 ${preview.schemaVersion}",
                    fontSize = 10.5.sp,
                    color = p.ink3,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = p.warnSoft,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "恢复会覆盖当前全部数据，且无法撤销。建议先备份当前数据。",
                        fontSize = 11.sp,
                        color = p.warnText,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("覆盖恢复", color = p.dangerText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = p.ink2) }
        }
    )
}

@Composable
fun ImportReportDialog(report: ImportReport, onDismiss: () -> Unit) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("导入完成", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportLine("成功导入", report.imported, p.successText)
                ReportLine("重复跳过", report.duplicate, p.ink2)
                ReportLine("状态跳过", report.skipped, p.ink2)
                ReportLine("解析失败", report.failed, if (report.failed > 0) p.dangerText else p.ink2)
                Text(
                    "导入后可在首页核对，重复文件再次导入会被自动跳过。",
                    fontSize = 10.5.sp,
                    color = p.ink3,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = p.brand) }
        }
    )
}

@Composable
private fun ReportLine(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = LedgerTheme.palette.ink2, modifier = Modifier.weight(1f))
        Text("$value 条", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun ImportPreviewDialog(
    preview: CsvImportPreview,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            color = p.bg,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "导入预览",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.ink,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = p.ink3,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onDismiss)
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    item {
                        LedgerCard(
                            shape = RoundedCornerShape(15.dp),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Text(
                                "${preview.source.label}账单 · ${preview.encoding}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = p.ink
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ImportStat("总行数", preview.totalRows.toString())
                                ImportStat("可导入", preview.importable.size.toString(), p.successText)
                                ImportStat("重复", preview.duplicateCount.toString(), p.warnText)
                                ImportStat("跳过", preview.skippedCount.toString())
                                ImportStat("失败", preview.failedCount.toString(), p.dangerText)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "前 20 条预览",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.ink3,
                            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                        )
                    }

                    itemsIndexed(preview.previewRows) { index, row ->
                        PreviewRow(row)
                        if (index < preview.previewRows.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = p.divider)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(p.card)
                            .clickable(enabled = !busy, onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (preview.importable.isEmpty() || busy) p.brand.copy(alpha = 0.45f) else p.brand)
                            .clickable(enabled = preview.importable.isNotEmpty() && !busy, onClick = onConfirm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (busy) "导入中…" else "确认导入 ${preview.importable.size} 条",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportStat(label: String, value: String, color: androidx.compose.ui.graphics.Color = LedgerTheme.palette.ink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.5.sp, color = LedgerTheme.palette.ink3, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PreviewRow(row: ImportedRow) {
    val p = LedgerTheme.palette
    val date = millisToLocalDate(row.occurredAt)
    val time = if (date == LocalDate.now()) {
        formatClock(row.occurredAt)
    } else {
        "${formatShortDate(row.occurredAt)} ${formatClock(row.occurredAt)}"
    }
    val typeLabel = when (row.type) {
        "INCOME" -> "收入"
        "TRANSFER" -> "不计收支"
        else -> "支出"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.bg)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.merchant ?: row.note ?: "未命名交易",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = p.ink,
                maxLines = 1
            )
            Text(
                "$time · $typeLabel${row.categoryName?.let { " · $it" } ?: ""}",
                fontSize = 10.sp,
                color = p.ink3,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            if (row.type == "INCOME") "+" else if (row.type == "EXPENSE") "-" else "",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (row.type) {
                "INCOME" -> p.income
                "EXPENSE" -> p.expense
                else -> p.ink2
            }
        )
        Text(
            formatMoney(row.amount),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (row.type) {
                "INCOME" -> p.income
                "EXPENSE" -> p.expense
                else -> p.ink2
            }
        )
    }
}
