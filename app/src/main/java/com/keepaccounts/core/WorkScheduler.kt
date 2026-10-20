package com.keepaccounts.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.keepaccounts.work.AutoBackupWorker
import com.keepaccounts.work.RecycleBinWorker
import java.util.concurrent.TimeUnit

/**
 * 后台任务的统一排期入口。
 *
 * 两个任务：回收站清理（每天）与自动备份（按用户周期，默认关闭）。
 * 都加了 flex 窗口 —— 系统会挑设备空闲时执行，没必要卡着整点醒一次 CPU。
 */
object WorkScheduler {

    private const val RECYCLE_WORK = "ka_recycle_cleanup"
    private const val BACKUP_WORK = "ka_auto_backup"

    fun scheduleRecycle(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecycleBinWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
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
                    // 备份只写本机私有目录，不需要网络。设 NOT_REQUIRED 让它在飞行模式也能跑
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
}
