package com.local.notebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TxnEntity::class,
        TxnEditLogEntity::class,
        BudgetEntity::class,
        NotificationRawEntity::class,
        RuleEntity::class,
        BackupRecordEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun txnEditLogDao(): TxnEditLogDao
    abstract fun budgetDao(): BudgetDao
    abstract fun notificationRawDao(): NotificationRawDao
    abstract fun ruleDao(): RuleDao
}
