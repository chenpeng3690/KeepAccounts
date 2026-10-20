package com.keepaccounts.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keepaccounts.KeepAccountsApp
import com.keepaccounts.data.local.db.BackupHistoryEntity
import com.keepaccounts.data.local.db.BackupTrigger
import java.io.File

/**
 * 自动备份。
 *
 * 写进应用私有目录 `files/backups/`，**不加密**：
 * 这一份是"本机快照"，受应用沙箱与系统文件级加密保护，设备丢了它也没意义。
 * 真正要跨设备迁移的，用设置页的手动备份（口令加密，存到你选的位置）。
 *
 * 保留最近 5 份，超出按文件名（含时间戳）排序删旧的。
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KeepAccountsApp).container
        return try {
            val bytes = container.backupRepo.export(password = null)
            val dir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
            val name = "auto-${System.currentTimeMillis()}.kabak"
            val file = File(dir, name)
            file.writeBytes(bytes)

            pruneOldFiles(dir, keep = KEEP_FILES)

            container.db.backupDao().insert(
                BackupHistoryEntity(
                    fileName = name,
                    location = file.absolutePath,
                    sizeBytes = bytes.size.toLong(),
                    trigger = BackupTrigger.AUTO,
                    encrypted = false,
                    txnCount = container.db.backupAccessDao().transactionCount(),
                    accountCount = container.db.backupAccessDao().accountCount(),
                    status = "success",
                    createdAt = System.currentTimeMillis()
                )
            )
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun pruneOldFiles(dir: File, keep: Int) {
        val files = dir.listFiles { f -> f.name.endsWith(".kabak") }
            ?.sortedByDescending { it.name }
            ?: return
        files.drop(keep).forEach { runCatching { it.delete() } }
    }

    companion object {
        private const val KEEP_FILES = 5
    }
}
