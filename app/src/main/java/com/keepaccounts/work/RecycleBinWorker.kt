package com.keepaccounts.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keepaccounts.KeepAccountsApp

/**
 * 回收站到期清理。
 *
 * 保留天数从 settings 读，用户改成 3/7/30 后下一次执行就按新值来，
 * 不需要重新排期 —— 排期只负责"每天跑一次"这件事。
 */
class RecycleBinWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KeepAccountsApp).container
        return try {
            val days = container.settingsRepo.recycleRetentionDays()
            container.txnRepo.purgeExpired(days)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
