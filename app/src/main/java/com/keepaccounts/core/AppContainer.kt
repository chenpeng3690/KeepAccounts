package com.keepaccounts.core

import android.content.Context
import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.DatabaseProvider
import com.keepaccounts.data.local.db.SeedData
import com.keepaccounts.data.repo.AccountRepository
import com.keepaccounts.data.repo.CategoryRepository
import com.keepaccounts.data.backup.BackupRepository
import com.keepaccounts.data.repo.SettingsRepository
import com.keepaccounts.notification.NotificationGate
import com.keepaccounts.notification.FeedbackNotifier
import com.keepaccounts.data.parse.BuiltinRules
import com.keepaccounts.data.repo.ImportRepository
import com.keepaccounts.data.repo.ParseRepository
import com.keepaccounts.data.repo.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手写依赖容器，替代 Hilt。
 *
 * 这个项目的依赖只有一层：DB → Repository → UI，没有多模块、没有按页面切 scope，
 * 引 Hilt 要额外挂 KSP 处理器和两个插件，是构建链上风险最高的环节。
 * 手写容器在这里足够，且零注解处理。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 应用级作用域：种子数据、回收站清理、备份这类不需要跟随页面生命周期的任务 */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase by lazy { DatabaseProvider.get(appContext) }

    val accountRepo: AccountRepository by lazy { AccountRepository(db) }
    val categoryRepo: CategoryRepository by lazy { CategoryRepository(db) }
    val txnRepo: TransactionRepository by lazy { TransactionRepository(db) }
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(db) }
    val backupRepo: BackupRepository by lazy { BackupRepository(db) }
    val parseRepo: ParseRepository by lazy { ParseRepository(db, txnRepo, settingsRepo, feedbackNotifier) }
    val feedbackNotifier: FeedbackNotifier by lazy { FeedbackNotifier(this, appContext) }
    val importRepo: ImportRepository by lazy { ImportRepository(db, txnRepo) }

    /** 首帧之前先把种子数据准备好，避免首页闪一下空列表 */
    fun bootstrap() {
        applicationScope.launch {
            runCatching { SeedData.ensure(db) }
            runCatching { BuiltinRules.ensure(db) }
            // 白名单常驻内存，启动时先灌一次，监听服务才能零延迟判断
            runCatching { NotificationGate.refresh(settingsRepo) }
            // 上次没解析完的采集记录补跑一遍
            runCatching { parseRepo.processCaptured() }
        }
    }
}
