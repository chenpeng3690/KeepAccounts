package com.keepaccounts.core

import android.content.Context
import com.keepaccounts.data.local.db.BackupHistoryEntity
import com.keepaccounts.data.local.db.BackupTrigger
import java.io.File

/**
 * 启动时顺手做的杂务：清理到期回收站、检查自动备份。
 *
 * 为什么不用 WorkManager：
 * 这台机器解析不了 `androidx.work`（缓存里没有，且拉不到 Google Maven），
 * 引一个下载不到的依赖会让整个工程没法编译。而这件杂务本身也不需要"准时"——
 * 打开 App 时补做一次完全够用：备份晚几小时没有损失，回收站清理晚几天也没有。
 *
 * 代价要说清楚：**App 长期不打开就不会清理也不会备份**。
 * 对一个自己用的记账 App 来说这是可接受的，比装不上强。
 */
object Housekeeping {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val KEEP_AUTO_FILES = 5

    suspend fun run(context: Context, container: AppContainer) {
        runCatching {
            container.txnRepo.purgeExpired(container.settingsRepo.recycleRetentionDays())
        }
        runCatching {
            autoBackupIfDue(context.applicationContext, container)
        }
    }

    private suspend fun autoBackupIfDue(context: Context, container: AppContainer) {
        if (!container.settingsRepo.autoBackupEnabled()) return

        val intervalDays = container.settingsRepo.autoBackupIntervalDays().coerceAtLeast(1)
        val lastAt = container.db.backupDao()
            .listByTrigger(BackupTrigger.AUTO)
            .firstOrNull()?.createdAt

        val due = lastAt == null || System.currentTimeMillis() - lastAt >= intervalDays * DAY_MS
        if (due) writeAutoBackup(context, container)
    }

    /** 生成一份本机快照，返回是否成功 */
    suspend fun writeAutoBackup(context: Context, container: AppContainer): Boolean {
        return runCatching {
            val bytes = container.backupRepo.export(password = null)
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val name = "auto-${System.currentTimeMillis()}.kabak"
            val file = File(dir, name)
            file.writeBytes(bytes)

            prune(dir, KEEP_AUTO_FILES)

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
            true
        }.getOrDefault(false)
    }

    private fun prune(dir: File, keep: Int) {
        val files = dir.listFiles { f -> f.name.endsWith(".kabak") }
            ?.sortedByDescending { it.name }
            ?: return
        files.drop(keep).forEach { runCatching { it.delete() } }
    }
}
