package com.keepaccounts.data.local.db

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * 加密数据库的构建入口。
 *
 * 唯一实例，延迟创建 —— 真正打开数据库发生在第一次查询时，因此放在主线程是安全的。
 *
 * 关于 native 库：SQLCipher 的 .so 不会自动加载，[loadCipherLibrary] 必须早于任何
 * Room 调用。漏掉这一步的症状很难定位 —— 编译期毫无提示，直到第一次查询数据库时
 * 在后台线程抛 UnsatisfiedLinkError，堆栈里全是协程帧，看起来和数据库毫无关系。
 * 所以把「加载」放在构建方法的第一行，而不是依赖某个初始化顺序。
 */
object DatabaseProvider {

    private val initLock = Any()

    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var cipherReady = false

    fun get(context: Context): AppDatabase =
        database ?: synchronized(initLock) {
            database ?: build(context.applicationContext).also { database = it }
        }

    private fun build(context: Context): AppDatabase {
        loadCipherLibrary()
        val key = LocalKeyStore.databaseKey(context)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
    }

    private fun loadCipherLibrary() {
        if (cipherReady) return
        synchronized(initLock) {
            if (!cipherReady) {
                System.loadLibrary("sqlcipher")
                cipherReady = true
            }
        }
    }
}
