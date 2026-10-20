package com.keepaccounts.data.local.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * 本机数据库的密钥材料。
 *
 * 为什么密钥要存在这里而不是常量或文件里：
 * 数据库文件落在应用私有目录，但 root 设备可以直接读走。SQLCipher 的全库加密
 * 只有在「密钥本身也受保护」时才成立，所以密钥交给 Keystore 派生出来的主密钥再包一层。
 *
 * 首次生成后永不变更 —— 一旦丢失，已有数据库就再也打不开了。
 * 至于换机迁移，那是「备份文件」的事，用用户口令加密，跟这把密钥无关。
 */
internal object LocalKeyStore {

    private const val PREFS_NAME = "ka_localkey"
    private const val ENTRY_DB_KEY = "db_key_b64"
    private const val KEY_SIZE_BYTES = 32

    fun databaseKey(context: Context): ByteArray {
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val stored = prefs.getString(ENTRY_DB_KEY, null)
        if (stored != null) {
            return android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        }

        val fresh = ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(ENTRY_DB_KEY, android.util.Base64.encodeToString(fresh, android.util.Base64.NO_WRAP))
            .apply()
        return fresh
    }

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context, "ka_master_key")
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
}
