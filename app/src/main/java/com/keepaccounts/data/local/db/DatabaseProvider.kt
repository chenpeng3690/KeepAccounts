package com.keepaccounts.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/**
 * 数据库单例：Room + SQLCipher，密钥由 Keystore 保护。
 *
 * **数据库加密密钥 ≠ 备份文件加密密钥**，这套两套密钥不能混用：
 * - 本机数据库：Keystore 包裹的随机密钥，用户无感，换机也不需要它。
 * - 备份文件：必须用「用户口令 + PBKDF2 派生」。因为 Keystore 密钥绑定设备，
 *   换机后它就不存在了，用它加密的备份等于造出一个永远打不开的文件。
 */
object DatabaseProvider {

    private const val PREFS_NAME = "ka_secure"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_hex"

    @Volatile
    private var instance: AppDatabase? = null

    @Volatile
    private var nativeLoaded = false

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    /**
     * 加载 libsqlcipher.so。
     *
     * 这一步极易漏，且漏了不会在编译期报错，只会在**第一次打开数据库**时抛
     * `UnsatisfiedLinkError: No implementation found for SQLiteConnection.nativeOpen(...)
     * - is the library loaded, e.g. System.loadLibrary?`
     * 表现为冷启动闪退，堆栈里全是协程帧，很难一眼看出是漏了 loadLibrary。
     * sqlcipher-android 的契约就是交给调用方加载，pom 里也不带 ReLinker。
     */
    private fun ensureNativeLibrary() {
        if (nativeLoaded) return
        synchronized(this) {
            if (nativeLoaded) return
            System.loadLibrary("sqlcipher")
            nativeLoaded = true
        }
    }

    private fun build(context: Context): AppDatabase {
        ensureNativeLibrary()
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(loadPassphrase(context)))
            .build()
    }

    /** 32 字节随机口令，以 hex 存进 EncryptedSharedPreferences（受 Keystore 保护） */
    private fun loadPassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        return if (existing != null) {
            existing.hexToBytes()
        } else {
            val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
            prefs.edit().putString(KEY_DB_PASSPHRASE, generated.toHex()).apply()
            generated
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "口令 hex 长度异常" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
