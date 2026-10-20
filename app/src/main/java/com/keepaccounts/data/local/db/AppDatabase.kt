package com.keepaccounts.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionEditEntity::class,
        RawEventEntity::class,
        RuleEntity::class,
        BudgetEntity::class,
        BackupHistoryEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun editLogDao(): EditLogDao
    abstract fun rawEventDao(): RawEventDao
    abstract fun ruleDao(): RuleDao
    abstract fun budgetDao(): BudgetDao
    /** 备份历史（backup_history 表） */
    abstract fun backupDao(): BackupDao
    /** 备份导入导出专用的全表访问口 */
    abstract fun backupAccessDao(): BackupAccessDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "keepaccounts.db"
    }
}
