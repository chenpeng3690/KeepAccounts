package com.keepaccounts.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.core.WorkScheduler
import com.keepaccounts.data.backup.BackupPreview
import com.keepaccounts.data.backup.RestoreMode
import com.keepaccounts.data.backup.RestoreReport
import com.keepaccounts.data.local.db.BackupTrigger
import com.keepaccounts.data.local.db.BackupHistoryEntity
import com.keepaccounts.data.local.db.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val retentionDays: Int = 7,
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalDays: Int = 7,
    val lastBackupAt: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val loaded: Boolean = false
)

class SettingsViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val retention = container.settingsRepo.recycleRetentionDays()
            val enabled = container.settingsRepo.autoBackupEnabled()
            val interval = container.settingsRepo.autoBackupIntervalDays()
            val last = container.db.backupDao().listAll().firstOrNull()?.createdAt
            _state.value = SettingsUiState(
                retentionDays = retention,
                autoBackupEnabled = enabled,
                autoBackupIntervalDays = interval,
                lastBackupAt = last,
                loaded = true
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun setRetention(days: Int) {
        viewModelScope.launch {
            container.settingsRepo.put(SettingKeys.RECYCLE_RETENTION_DAYS, days.toString())
            _state.value = _state.value.copy(retentionDays = days)
            // 改成 3 天后，已经超过 3 天的记录应该立刻清掉，而不是等明天那班 worker
            container.txnRepo.purgeExpired(days)
        }
    }

    fun setAutoBackup(enabled: Boolean, intervalDays: Int = _state.value.autoBackupIntervalDays) {
        viewModelScope.launch {
            container.settingsRepo.put(SettingKeys.AUTO_BACKUP_ENABLED, enabled.toString())
            container.settingsRepo.put(SettingKeys.AUTO_BACKUP_INTERVAL_DAYS, intervalDays.toString())
            _state.value = _state.value.copy(
                autoBackupEnabled = enabled,
                autoBackupIntervalDays = intervalDays
            )
            WorkScheduler.scheduleAutoBackup(appContext, if (enabled) intervalDays else 0)
        }
    }

    fun setBusy(busy: Boolean) {
        _state.value = _state.value.copy(busy = busy)
    }

    fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    suspend fun buildBackup(password: String?): ByteArray =
        container.backupRepo.export(password)

    fun peekBackup(bytes: ByteArray, password: String?): BackupPreview =
        container.backupRepo.peek(bytes, password)

    fun isEncrypted(bytes: ByteArray): Boolean = container.backupRepo.isEncrypted(bytes)

    suspend fun restore(bytes: ByteArray, password: String?, mode: RestoreMode): RestoreReport =
        container.backupRepo.restore(bytes, password, mode)

    suspend fun recordManualBackup(fileName: String, sizeBytes: Long) {
        container.db.backupDao().insert(
            BackupHistoryEntity(
                fileName = fileName,
                location = "SAF",
                sizeBytes = sizeBytes,
                trigger = BackupTrigger.MANUAL,
                encrypted = true,
                txnCount = container.db.backupAccessDao().transactionCount(),
                accountCount = container.db.backupAccessDao().accountCount(),
                status = "success",
                createdAt = System.currentTimeMillis()
            )
        )
        _state.value = _state.value.copy(lastBackupAt = System.currentTimeMillis())
    }
}
