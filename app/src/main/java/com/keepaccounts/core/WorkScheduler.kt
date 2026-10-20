package com.keepaccounts.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.keepaccounts.work.AutoBackupWorker
import com.keepaccounts.work.RecycleBinWorker
import java.util.concurrent.TimeUnit

/**
 * 后台任务的统一排期入口。
 *
 * 两个周期任务：回收站清理（每天）、自动备份（按用户周期，默认关闭）。
 * 都加了 constraints 与 flex —— 让系统挑设备空闲时跑，没必要为一件不急的事硬醒 CPU。
 */
object WorkScheduler {

    private const val RECYCLE_WORK = "ka_recycle_cleanup"
    private const val BACKUP_WORK = "ka_auto_backup"

    fun scheduleRecycle(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecycleBinWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            // 首次延迟到装好 3 小时后，避开刚启动时的忙乱
            .setInitialDelay(3, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECYCLE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** intervalDays <= 0 表示关闭 */
    fun scheduleAutoBackup(context: Context, intervalDays: Int) {
        val manager = WorkManager.getInstance(context)
        if (intervalDays <= 0) {
            manager.cancelUniqueWork(BACKUP_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalDays.toLong(), TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    // 备份只写本机私有目录，不需要网络。设 NOT_REQUIRED 让飞行模式下也能跑
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        manager.enqueueUniquePeriodicWork(
            BACKUP_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** 用户刚打开自动备份开关时，立刻跑一次，让他当场看到生效 */
    fun runAutoBackupNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
