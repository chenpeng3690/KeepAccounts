package com.local.notebook.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.local.notebook.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerEntryPoint {
    fun backupManager(): BackupManager
    fun settingsRepository(): SettingsRepository
}

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val entry = EntryPointAccessors.fromApplication(
                applicationContext,
                BackupWorkerEntryPoint::class.java
            )
            val manager = entry.backupManager()
            val settings = entry.settingsRepository()

            val dir = File(applicationContext.filesDir, "auto_backup").apply { mkdirs() }
            val stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US))
            val file = File(dir, "NotiLedger-auto-$stamp.zip")
            manager.writeBackupToFile(file)
            val now = System.currentTimeMillis()
            settings.setLastAutoBackupAt(now)
            val periodDays = settings.autoBackupPeriodDays.first().coerceAtLeast(1)
            settings.setNextAutoBackupAt(now + TimeUnit.DAYS.toMillis(periodDays.toLong()))

            val backups = dir.listFiles { candidate ->
                candidate.isFile && candidate.name.endsWith(".zip")
            }?.sortedBy { it.lastModified() }.orEmpty()
            backups.dropLast(MAX_FILES).forEach { it.delete() }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private companion object {
        const val MAX_FILES = 5
    }
}
