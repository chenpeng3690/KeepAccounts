package com.keepaccounts

import android.app.Application
import com.keepaccounts.core.AppContainer
import com.keepaccounts.core.Housekeeping
import kotlinx.coroutines.launch

class KeepAccountsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 数据库懒加载：这里只是启动种子协程，不会在主线程打开 DB
        container.bootstrap()

        // 回收站清理与自动备份都在打开 App 时补做，不依赖 WorkManager
        container.applicationScope.launch {
            Housekeeping.run(this@KeepAccountsApp, container)
        }
    }
}
