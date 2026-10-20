package com.keepaccounts

import android.app.Application
import com.keepaccounts.core.AppContainer

class KeepAccountsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 数据库懒加载：这里只是启动种子协程，不会在主线程打开 DB
        container.bootstrap()
    }
}
