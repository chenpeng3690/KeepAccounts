package com.keepaccounts

import android.app.Application
import com.keepaccounts.core.AppContainer
import com.keepaccounts.core.WorkScheduler
import com.keepaccounts.notification.AppForeground
import kotlinx.coroutines.launch

class KeepAccountsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 数据库懒加载：这里只是启动种子协程，不会在主线程打开 DB
        container.bootstrap()

        // 浮悬提示：通知渠道 + 前台状态追踪
        container.feedbackNotifier.ensureChannel()
        AppForeground.install(this)

        WorkScheduler.scheduleRecycle(this)
        container.applicationScope.launch {
            val enabled = container.settingsRepo.autoBackupEnabled()
            val interval = container.settingsRepo.autoBackupIntervalDays()
            WorkScheduler.scheduleAutoBackup(this@KeepAccountsApp, if (enabled) interval else 0)
        }
    }
}
