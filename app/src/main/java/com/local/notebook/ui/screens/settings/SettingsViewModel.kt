package com.local.notebook.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.local.notebook.data.backup.AutoBackupWorker
import com.local.notebook.data.backup.BackupManager
import com.local.notebook.data.backup.BackupPreview
import com.local.notebook.data.importer.CsvImportPreview
import com.local.notebook.data.importer.CsvImporter
import com.local.notebook.data.importer.ImportReport
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsMessage(val text: String, val error: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    repository: LedgerRepository,
    private val backupManager: BackupManager,
    private val csvImporter: CsvImporter
) : ViewModel() {

    val retentionDays: StateFlow<Int> = settings.trashRetentionDays.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 7
    )

    val whitelistPackages: StateFlow<Set<String>> = settings.whitelistPackages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet()
    )

    val ruleCount: StateFlow<Int> = repository.rules
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val themeMode: StateFlow<String> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "SYSTEM"
    )

    val feedbackMode: StateFlow<String> = settings.feedbackMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "ALL"
    )

    val autoBackupEnabled: StateFlow<Boolean> = settings.autoBackupEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val autoBackupPeriodDays: StateFlow<Int> = settings.autoBackupPeriodDays.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 7
    )

    val lastAutoBackupAt: StateFlow<Long> = settings.lastAutoBackupAt.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0L
    )

    val nextAutoBackupAt: StateFlow<Long> = settings.nextAutoBackupAt.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0L
    )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message

    private val _restorePreview = MutableStateFlow<BackupPreview?>(null)
    val restorePreview: StateFlow<BackupPreview?> = _restorePreview

    private val _importPreview = MutableStateFlow<CsvImportPreview?>(null)
    val importPreview: StateFlow<CsvImportPreview?> = _importPreview

    private val _importReport = MutableStateFlow<ImportReport?>(null)
    val importReport: StateFlow<ImportReport?> = _importReport

    private var pendingRestoreUri: Uri? = null

    init {
        viewModelScope.launch {
            if (settings.autoBackupEnabled.first()) {
                scheduleAutoBackup(
                    settings.autoBackupPeriodDays.first(),
                    replace = false
                )
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setFeedbackMode(mode: String) {
        viewModelScope.launch { settings.setFeedbackMode(mode) }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoBackupEnabled(enabled)
            if (enabled) {
                val days = settings.autoBackupPeriodDays.first().coerceAtLeast(1)
                settings.setNextAutoBackupAt(
                    System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong())
                )
                scheduleAutoBackup(days, replace = true)
            } else {
                settings.setNextAutoBackupAt(0L)
                WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK)
            }
        }
    }

    fun setAutoBackupPeriod(days: Int) {
        viewModelScope.launch {
            settings.setAutoBackupPeriodDays(days)
            if (settings.autoBackupEnabled.first()) {
                settings.setNextAutoBackupAt(
                    System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong())
                )
                scheduleAutoBackup(days, replace = true)
            }
        }
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        backupManager.writeBackup(output)
                    } ?: error("无法写入所选位置")
                }
            }.onSuccess {
                _message.value = SettingsMessage("备份完成")
            }.onFailure {
                _message.value = SettingsMessage("备份失败：${it.message ?: "未知错误"}", error = true)
            }
            _busy.value = false
        }
    }

    fun prepareRestore(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { backupManager.readPreview(uri) }
                .onSuccess {
                    pendingRestoreUri = uri
                    _restorePreview.value = it
                }
                .onFailure {
                    _message.value = SettingsMessage("无法读取备份：${it.message ?: "未知错误"}", error = true)
                }
            _busy.value = false
        }
    }

    fun confirmRestore() {
        val uri = pendingRestoreUri ?: return
        viewModelScope.launch {
            _busy.value = true
            _restorePreview.value = null
            runCatching { backupManager.restore(uri) }
                .onSuccess { _message.value = SettingsMessage("恢复完成，数据已覆盖") }
                .onFailure {
                    _message.value = SettingsMessage("恢复失败：${it.message ?: "未知错误"}", error = true)
                }
            pendingRestoreUri = null
            _busy.value = false
        }
    }

    fun cancelRestore() {
        _restorePreview.value = null
        pendingRestoreUri = null
    }

    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { csvImporter.parse(uri) }
                .onSuccess { _importPreview.value = it }
                .onFailure {
                    _message.value = SettingsMessage("解析失败：${it.message ?: "未知错误"}", error = true)
                }
            _busy.value = false
        }
    }

    fun confirmImport() {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            _busy.value = true
            _importPreview.value = null
            runCatching { csvImporter.commit(preview) }
                .onSuccess { _importReport.value = it }
                .onFailure {
                    _message.value = SettingsMessage("导入失败：${it.message ?: "未知错误"}", error = true)
                }
            _busy.value = false
        }
    }

    fun cancelImport() {
        _importPreview.value = null
    }

    fun dismissImportReport() {
        _importReport.value = null
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { csvImporter.exportCsv(uri) }
                .onSuccess { _message.value = SettingsMessage("账单已导出") }
                .onFailure {
                    _message.value = SettingsMessage("导出失败：${it.message ?: "未知错误"}", error = true)
                }
            _busy.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun scheduleAutoBackup(days: Int, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days.toLong(), TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_BACKUP_WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val AUTO_BACKUP_WORK = "notiledger_auto_backup"
    }
}
