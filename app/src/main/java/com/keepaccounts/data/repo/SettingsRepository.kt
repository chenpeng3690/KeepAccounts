package com.keepaccounts.data.repo

import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.AppSettingEntity
import com.keepaccounts.data.local.db.SettingKeys
import kotlinx.coroutines.flow.Flow

/**
 * 设置读写。全部落在 `app_settings` 表，不引 DataStore ——
 * 键值少、要跟账本一起备份，放在同一个数据库里更省事。
 */
class SettingsRepository(private val db: AppDatabase) {

    private val dao = db.settingsDao()

    fun observe(key: String): Flow<String?> = dao.observe(key)

    suspend fun get(key: String): String? = dao.get(key)

    suspend fun put(key: String, value: String) = dao.put(AppSettingEntity(key, value))

    suspend fun getInt(key: String, fallback: Int): Int =
        dao.get(key)?.toIntOrNull() ?: fallback

    suspend fun getBoolean(key: String, fallback: Boolean): Boolean =
        dao.get(key)?.toBooleanStrictOrNull() ?: fallback

    suspend fun recycleRetentionDays(): Int =
        getInt(SettingKeys.RECYCLE_RETENTION_DAYS, 7)

    suspend fun autoBackupEnabled(): Boolean =
        getBoolean(SettingKeys.AUTO_BACKUP_ENABLED, false)

    suspend fun autoBackupIntervalDays(): Int =
        getInt(SettingKeys.AUTO_BACKUP_INTERVAL_DAYS, 7)
}
