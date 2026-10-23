package com.local.notebook.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.service.NotificationAccess
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.ScreenHeader
import com.local.notebook.ui.components.SectionHeader
import com.local.notebook.ui.components.SettingsRow
import com.local.notebook.ui.screens.settings.ChoiceDialog
import com.local.notebook.ui.screens.settings.ImportPreviewDialog
import com.local.notebook.ui.screens.settings.ImportReportDialog
import com.local.notebook.ui.screens.settings.RestorePreviewDialog
import com.local.notebook.ui.screens.settings.SettingsMessageDialog
import com.local.notebook.ui.screens.settings.SettingsViewModel
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.theme.containerColor
import com.local.notebook.ui.theme.contentColor
import com.local.notebook.ui.util.openNotificationSettings
import com.local.notebook.ui.util.rememberNotificationAccessEnabled
import com.local.notebook.ui.util.rememberNotificationsEnabled
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenTrash: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAutoBackup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val whitelist by viewModel.whitelistPackages.collectAsStateWithLifecycle()
    val ruleCount by viewModel.ruleCount.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val feedbackMode by viewModel.feedbackMode.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backupPeriod by viewModel.autoBackupPeriodDays.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    val accessEnabled = rememberNotificationAccessEnabled()
    val notificationsEnabled = rememberNotificationsEnabled()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::createBackup) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::prepareRestore) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::prepareImport) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(viewModel::exportCsv) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(title = "设置")
        if (busy) {
            Text(
                "正在处理，请稍候…",
                fontSize = 10.5.sp,
                color = p.brand,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )
        }

        SectionHeader(title = "通知与解析", modifier = Modifier.padding(top = 0.dp))
        LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(0.dp)) {
            SettingsRow(
                icon = R.drawable.ic_tab_pending,
                iconTint = if (accessEnabled) p.successText else p.dangerText,
                iconBg = if (accessEnabled) p.successSoft else p.dangerSoft,
                label = "通知使用权",
                value = if (accessEnabled) "已开启" else "未开启",
                valueColor = if (accessEnabled) p.successText else p.dangerText,
                dotColor = if (accessEnabled) p.income else p.expense,
                showDot = true,
                showChevron = false,
                onClick = { NotificationAccess.openSettings(context) }
            )
            SettingsRow(
                icon = R.drawable.ic_grid,
                iconTint = p.brandSoftText,
                iconBg = p.brandSoft,
                label = "白名单",
                value = if (whitelist.isEmpty()) "未选择应用" else "已开启 ${whitelist.size} 个",
                showDivider = true,
                onClick = onOpenWhitelist
            )
            SettingsRow(
                icon = R.drawable.ic_rules,
                iconTint = p.warnText,
                iconBg = p.warnSoft,
                label = "解析规则",
                value = "$ruleCount 条",
                showDivider = true,
                onClick = onOpenRules
            )
            SettingsRow(
                icon = R.drawable.ic_tab_pending,
                iconTint = p.brandSoftText,
                iconBg = p.brandSoft,
                label = "记账提示",
                value = feedbackLabel(feedbackMode),
                showDivider = true,
                onClick = { showFeedbackDialog = true }
            )
        }
        Text(
            "静音时段 23:00–07:00 只静默投递，不弹横幅",
            fontSize = 10.sp,
            color = p.ink3,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp)
        )
        if (!notificationsEnabled && feedbackMode != "NONE") {
            Text(
                "系统通知权限未开启，横幅无法显示 · 去开启",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = p.warnText,
                modifier = Modifier
                    .padding(start = 2.dp, top = 6.dp)
                    .clickable { openNotificationSettings(context) }
            )
        }

        SectionHeader(title = "数据")
        LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(0.dp)) {
            SettingsRow(
                icon = R.drawable.ic_backup,
                iconTint = p.brandSoftText,
                iconBg = p.brandSoft,
                label = "立即备份",
                value = "选择保存位置",
                onClick = {
                    backupLauncher.launch("NotiLedger-backup-${fileStamp()}.zip")
                }
            )
            SettingsRow(
                icon = R.drawable.ic_import,
                iconTint = p.brandSoftText,
                iconBg = p.brandSoft,
                label = "恢复备份",
                value = "从 zip 恢复",
                showDivider = true,
                onClick = { restoreLauncher.launch(arrayOf("application/zip")) }
            )
            SettingsRow(
                icon = R.drawable.ic_clock,
                iconTint = p.brandSoftText,
                iconBg = p.brandSoft,
                label = "自动备份",
                value = if (autoBackupEnabled) "已开启 · 每 $backupPeriod 天" else "未开启",
                showDivider = true,
                onClick = onOpenAutoBackup
            )
            SettingsRow(
                icon = R.drawable.ic_import,
                iconTint = p.successText,
                iconBg = p.successSoft,
                label = "导入账单",
                value = "微信 / 支付宝",
                showDivider = true,
                onClick = { importLauncher.launch(arrayOf("*/*")) }
            )
            SettingsRow(
                icon = R.drawable.ic_backup,
                iconTint = p.successText,
                iconBg = p.successSoft,
                label = "导出账单",
                value = "UTF-8 CSV",
                showDivider = true,
                onClick = {
                    exportLauncher.launch("NotiLedger-transactions-${fileStamp()}.csv")
                }
            )
        }

        SectionHeader(title = "其他")
        LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(0.dp)) {
            SettingsRow(
                icon = R.drawable.ic_trash,
                iconTint = p.warnText,
                iconBg = p.warnSoft,
                label = "回收站保留",
                value = "$retentionDays 天",
                onClick = onOpenTrash
            )
            SettingsRow(
                icon = R.drawable.ic_moon,
                iconTint = LedgerTone.PURPLE.contentColor(),
                iconBg = LedgerTone.PURPLE.containerColor(),
                label = "深色模式",
                value = themeLabel(themeMode),
                showDivider = true,
                onClick = { showThemeDialog = true }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("通知记账 v1.0", fontSize = 11.sp, color = p.ink3)
            Text(
                "本应用不联网，数据只存在这台手机上",
                fontSize = 10.sp,
                color = p.ink3.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = "深色模式",
            options = listOf(
                "跟随系统" to "SYSTEM",
                "浅色" to "LIGHT",
                "深色" to "DARK"
            ),
            current = themeMode,
            onSelect = viewModel::setThemeMode,
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showFeedbackDialog) {
        ChoiceDialog(
            title = "记账提示",
            options = listOf(
                "全部提示" to "ALL",
                "仅成功入账" to "SUCCESS",
                "不提示" to "NONE"
            ),
            current = feedbackMode,
            onSelect = viewModel::setFeedbackMode,
            onDismiss = { showFeedbackDialog = false }
        )
    }
    restorePreview?.let {
        RestorePreviewDialog(
            preview = it,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore
        )
    }
    importPreview?.let {
        ImportPreviewDialog(
            preview = it,
            busy = busy,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport
        )
    }
    importReport?.let {
        ImportReportDialog(report = it, onDismiss = viewModel::dismissImportReport)
    }
    message?.let {
        SettingsMessageDialog(message = it, onDismiss = viewModel::consumeMessage)
    }
}

private fun feedbackLabel(mode: String): String = when (mode) {
    "SUCCESS" -> "仅成功入账"
    "NONE" -> "不提示"
    else -> "全部提示"
}

private fun themeLabel(mode: String): String = when (mode) {
    "LIGHT" -> "浅色"
    "DARK" -> "深色"
    else -> "跟随系统"
}

private fun fileStamp(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US))
