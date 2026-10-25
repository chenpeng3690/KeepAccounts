package com.local.notebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TxnEntity::class,
        TxnEditLogEntity::class,
        BudgetEntity::class,
        NotificationRawEntity::class,
        TransactionKeywordEntity::class,
        BackupRecordEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun txnEditLogDao(): TxnEditLogDao
    abstract fun budgetDao(): BudgetDao
    abstract fun notificationRawDao(): NotificationRawDao
    abstract fun transactionKeywordDao(): TransactionKeywordDao

    companion object {
        /** v1 → v2：删除本地规则表，新增通知关键词表与 AI 结果字段，账本数据全部保留。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `rule`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_keyword` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`word` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`isBuiltin` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `notification_raw` ADD COLUMN `aiResponse` TEXT")
                db.execSQL("ALTER TABLE `notification_raw` ADD COLUMN `aiModel` TEXT")
                db.execSQL("ALTER TABLE `notification_raw` ADD COLUMN `aiError` TEXT")
            }
        }
    }
}
