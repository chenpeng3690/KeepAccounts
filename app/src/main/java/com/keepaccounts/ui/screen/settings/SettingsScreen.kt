package com.keepaccounts.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.backup.BackupPreview
import com.keepaccounts.data.backup.RestoreMode
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.ScreenHeader
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onOpenTrash: () -> Unit) {
    val context = LocalContext.current
    val vm = kaViewModel { SettingsViewModel(it, context.applicationContext) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<BackupPreview?>(null) }
    var restorePassword by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val password = pendingPassword
        val fileName = "KeepAccounts-${System.currentTimeMillis()}.kabak"
        scope.launch {
            vm.setBusy(true)
            runCatching {
                val bytes = vm.buildBackup(password)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("无法写入所选位置")
                vm.recordManualBackup(fileName, bytes.size.toLong())
                vm.showMessage("备份完成，共 ${bytes.size / 1024} KB")
            }.onFailure { vm.showMessage(it.message ?: "备份失败") }
            vm.setBusy(false)
            pendingPassword = null
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.setBusy(true)
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            vm.setBusy(false)

            if (bytes == null) {
                vm.showMessage("读取文件失败")
                return@launch
            }
            if (vm.isEncrypted(bytes)) {
                pendingRestoreBytes = bytes
                showRestorePassword = true
            } else {
                pendingRestoreBytes = bytes
                restorePreview = runCatching { vm.peekBackup(bytes, null) }
                    .onFailure { vm.showMessage(it.message ?: "这个文件读不了") }
                    .getOrNull()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(title = "设置")

        // ---------- 通知与解析 ----------
        SectionLabel(text = "通知与解析")
        SettingGroup {
            SettingRow(
                title = "通知使用权",
                value = "未开启 · 第 5 步接上",
                enabled = false
            )
            SettingRow(title = "应用白名单", value = "第 5 步接上", enabled = false)
            SettingRow(title = "解析规则", value = "第 6 步接上", enabled = false)
        }

        // ---------- 数据 ----------
        SectionLabel(text = "数据")
        SettingGroup {
            SettingRow(
                title = "立即备份",
                value = state.lastBackupAt?.let { "上次 ${formatDateTime(it)}" } ?: "还没有备份过",
                enabled = !state.busy,
                onClick = { showBackupPassword = true }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "自动备份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "默认关闭。每次打开 App 时检查，到期就在本机生成一份快照，保留最近 5 份",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = state.autoBackupEnabled,
                    onCheckedChange = { vm.setAutoBackup(it) }
                )
            }
            if (state.autoBackupEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 3, 7, 30).forEach { days ->
                        val selected = state.autoBackupIntervalDays == days
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { vm.setAutoBackup(true, days) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "$days 天",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            SettingRow(
                title = "从文件恢复",
                value = "选择 .kabak 文件",
                enabled = !state.busy,
                onClick = { openLauncher.launch(arrayOf("*/*")) }
            )
            SettingRow(
                title = "回收站",
                value = "保留 ${state.retentionDays} 天",
                onClick = onOpenTrash
            )
        }

        // ---------- 其他 ----------
        SectionLabel(text = "其他")
        SettingGroup {
            SettingRow(title = "深色模式", value = "跟随系统", enabled = false)
            SettingRow(
                title = "记账提示",
                value = "第 7 步接上",
                enabled = false
            )
        }

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "通知记账 v0.1.0",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "本应用不联网，数据只存在这台手机上",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (state.busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("处理中…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在读写账本数据")
                }
            }
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.clearMessage() },
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.clearMessage() }) { Text("好") }
            }
        )
    }

    errorText?.let { message ->
        AlertDialog(
            onDismissRequest = { errorText = null },
            title = { Text("出错了") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorText = null }) { Text("好") } }
        )
    }

    if (showBackupPassword) {
        PasswordDialog(
            title = "给备份设个密码",
            description = "留空则不加密。设了密码后，恢复时必须输入同一个密码 —— 忘了就打不开。",
            allowEmpty = true,
            onDismiss = { showBackupPassword = false },
            onConfirm = { pwd ->
                showBackupPassword = false
                pendingPassword = pwd
                createLauncher.launch(
                    "KeepAccounts-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())}.kabak"
                )
            }
        )
    }

    if (showRestorePassword) {
        PasswordDialog(
            title = "这个备份有密码",
            description = "输入备份时设置的密码",
            allowEmpty = false,
            onDismiss = { showRestorePassword = false; pendingRestoreBytes = null },
            onConfirm = { pwd ->
                val bytes = pendingRestoreBytes
                showRestorePassword = false
                restorePassword = pwd
                if (bytes != null) {
                    scope.launch {
                        vm.setBusy(true)
                        runCatching { vm.peekBackup(bytes, pwd) }
                            .onSuccess { restorePreview = it }
                            .onFailure { vm.showMessage(it.message ?: "密码不对") }
                        vm.setBusy(false)
                    }
                }
            }
        )
    }

    restorePreview?.let { preview ->
        RestorePreviewDialog(
            preview = preview,
            onDismiss = { restorePreview = null },
            onRestore = { mode ->
                val bytes = pendingRestoreBytes
                val pwd = restorePassword
                restorePreview = null
                if (bytes != null) {
                    scope.launch {
                        vm.setBusy(true)
                        runCatching { vm.restore(bytes, pwd, mode) }
                            .onSuccess {
                                vm.showMessage(
                                    "恢复完成：账户 ${it.accounts} · 分类 ${it.categories} · " +
                                        "账单 ${it.transactions}" +
                                        if (it.skipped > 0) "，跳过重复 ${it.skipped} 条" else ""
                                )
                            }
                            .onFailure { vm.showMessage(it.message ?: "恢复失败") }
                        vm.setBusy(false)
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingGroup(content: @Composable () -> Unit) {
    KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        content()
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null && enabled) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    allowEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.takeIf { it.isNotBlank() }) },
                enabled = allowEmpty || text.isNotBlank()
            ) { Text(if (allowEmpty && text.isBlank()) "不加密" else "确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RestorePreviewDialog(
    preview: BackupPreview,
    onDismiss: () -> Unit,
    onRestore: (RestoreMode) -> Unit
) {
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认恢复") },
        text = {
            Column {
                InfoLine("备份时间", formatDateTime(preview.createdAt))
                InfoLine("账户", "${preview.accountCount} 个")
                InfoLine("分类", "${preview.categoryCount} 个")
                InfoLine("账单", "${preview.transactionCount} 条")
                InfoLine("加密", if (preview.encrypted) "是" else "否")
                Spacer(Modifier.height(14.dp))
                ModeOption(
                    title = "合并追加",
                    desc = "保留现有数据，重复的账单跳过",
                    selected = mode == RestoreMode.MERGE
                ) { mode = RestoreMode.MERGE }
                Spacer(Modifier.height(8.dp))
                ModeOption(
                    title = "覆盖全部",
                    desc = "先清空现有账本再导入，不可撤销",
                    selected = mode == RestoreMode.OVERWRITE
                ) { mode = RestoreMode.OVERWRITE }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRestore(mode) }) {
                Text(
                    text = "开始恢复",
                    color = if (mode == RestoreMode.OVERWRITE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ModeOption(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Text(
                text = "●",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatDateTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
