package com.keepaccounts.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 备份专用的读写口。
 *
 * 单独一个 DAO 而不是往业务 DAO 里加方法，是为了让「谁都能读全表」这件事只出现在一处：
 * 业务查询必须带 `deleted_at IS NULL`，而备份恰恰需要软删除的数据和全部设置。
 * 把这两种语义混在同一个 DAO 里，迟早有人拿错方法。
 */
@Dao
interface BackupAccessDao {

    @Query("SELECT * FROM accounts")
    suspend fun allAccounts(): List<AccountEntity>

    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<CategoryEntity>

    @Query("SELECT * FROM transactions")
    suspend fun allTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM rules")
    suspend fun allRules(): List<RuleEntity>

    @Query("SELECT * FROM budgets")
    suspend fun allBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM app_settings")
    suspend fun allSettings(): List<AppSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(items: List<RuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(items: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(items: List<AppSettingEntity>)

    /**
     * 补写退款指向。
     * 退款记录通过 refund_of 自引用原支出，导入时如果被引用的那条还没写进去，
     * 外键会直接失败，所以先全量插入（refund_of 置空），再逐条补。
     */
    @Query("UPDATE transactions SET refund_of = :refundOf WHERE id = :id")
    suspend fun setRefundOf(id: Long, refundOf: Long?)

    // 清空顺序必须从子表到父表，否则外键 RESTRICT 会直接失败
    @Query("DELETE FROM transaction_edits")
    suspend fun clearEditLogs()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM raw_events")
    suspend fun clearRawEvents()

    @Query("DELETE FROM rules")
    suspend fun clearRules()

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM app_settings")
    suspend fun clearSettings()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int
}
