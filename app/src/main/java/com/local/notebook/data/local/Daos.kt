package com.local.notebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM account ORDER BY sortOrder, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM account")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<AccountEntity>): List<Long>

    @Insert
    suspend fun insert(item: AccountEntity): Long

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("UPDATE account SET currentBalance = :balance WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Long)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM account WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT COUNT(*) FROM txn WHERE deletedAt IS NULL " +
            "AND (accountId = :id OR toAccountId = :id)"
    )
    suspend fun activeTxnCount(id: Long): Int

    @Query("DELETE FROM account")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY type, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY type, sortOrder, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>): List<Long>

    @Insert
    suspend fun insert(item: CategoryEntity): Long

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("DELETE FROM category")
    suspend fun deleteAll()
}

@Dao
interface TxnDao {
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amount AS amount, t.accountId AS accountId,
               t.toAccountId AS toAccountId, t.categoryId AS categoryId, t.merchant AS merchant,
               t.note AS note, t.occurredAt AS occurredAt, t.source AS source, t.status AS status,
               t.editCount AS editCount, t.deletedAt AS deletedAt,
               a.name AS accountName, ta.name AS toAccountName,
               c.name AS categoryName, c.icon AS categoryIcon, c.tone AS categoryTone
        FROM txn t
        LEFT JOIN account a ON a.id = t.accountId
        LEFT JOIN account ta ON ta.id = t.toAccountId
        LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.deletedAt IS NULL AND t.status = 'NORMAL'
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observeActiveRows(): Flow<List<TxnRow>>

    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amount AS amount, t.accountId AS accountId,
               t.toAccountId AS toAccountId, t.categoryId AS categoryId, t.merchant AS merchant,
               t.note AS note, t.occurredAt AS occurredAt, t.source AS source, t.status AS status,
               t.editCount AS editCount, t.deletedAt AS deletedAt,
               a.name AS accountName, ta.name AS toAccountName,
               c.name AS categoryName, c.icon AS categoryIcon, c.tone AS categoryTone
        FROM txn t
        LEFT JOIN account a ON a.id = t.accountId
        LEFT JOIN account ta ON ta.id = t.toAccountId
        LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.deletedAt IS NOT NULL
        ORDER BY t.deletedAt DESC, t.id DESC
        """
    )
    fun observeDeletedRows(): Flow<List<TxnRow>>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun getById(id: Long): TxnEntity?

    @Query("SELECT * FROM txn WHERE deletedAt IS NULL AND status = 'NORMAL'")
    suspend fun getActiveForBalance(): List<TxnEntity>

    @Query("SELECT * FROM txn WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun getDeletedBefore(cutoff: Long): List<TxnEntity>

    @Query("SELECT COUNT(*) FROM txn")
    suspend fun count(): Int

    @Insert
    suspend fun insert(txn: TxnEntity): Long

    @Insert
    suspend fun insertAll(items: List<TxnEntity>)

    @Update
    suspend fun update(txn: TxnEntity)

    @Query("UPDATE txn SET deletedAt = :now, deleteReason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long, reason: String?)

    @Query("UPDATE txn SET deletedAt = NULL, deleteReason = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query("DELETE FROM txn WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM txn WHERE dedupeKey = :key")
    suspend fun countByDedupeKey(key: String): Int

    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amount AS amount, t.merchant AS merchant,
               t.note AS note, t.occurredAt AS occurredAt, t.accountId AS accountId,
               t.categoryId AS categoryId, t.ruleId AS ruleId, t.rawNotificationId AS rawNotificationId,
               a.name AS accountName, c.name AS categoryName, c.icon AS categoryIcon, c.tone AS categoryTone,
               n.appLabel AS appLabel, n.packageName AS packageName, n.title AS rawTitle,
               n.text AS rawText, n.receivedAt AS rawReceivedAt, n.matchResult AS rawResult
        FROM txn t
        LEFT JOIN account a ON a.id = t.accountId
        LEFT JOIN category c ON c.id = t.categoryId
        LEFT JOIN notification_raw n ON n.id = t.rawNotificationId
        WHERE t.status = 'PENDING' AND t.deletedAt IS NULL
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observePendingRows(): Flow<List<PendingRow>>

    @Query("SELECT COUNT(*) FROM txn WHERE status = 'PENDING' AND deletedAt IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM txn WHERE status = 'PENDING' AND deletedAt IS NULL")
    suspend fun getPendingEntities(): List<TxnEntity>

    @Query("UPDATE txn SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long)

    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amount AS amount, t.accountId AS accountId,
               t.toAccountId AS toAccountId, t.categoryId AS categoryId, t.merchant AS merchant,
               t.note AS note, t.occurredAt AS occurredAt, t.source AS source, t.status AS status,
               t.editCount AS editCount, t.deletedAt AS deletedAt,
               a.name AS accountName, ta.name AS toAccountName,
               c.name AS categoryName, c.icon AS categoryIcon, c.tone AS categoryTone
        FROM txn t
        LEFT JOIN account a ON a.id = t.accountId
        LEFT JOIN account ta ON ta.id = t.toAccountId
        LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.deletedAt IS NULL AND t.status = 'NORMAL'
          AND (t.accountId = :accountId OR t.toAccountId = :accountId)
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observeRowsForAccount(accountId: Long): Flow<List<TxnRow>>

    @Query("UPDATE txn SET accountId = :targetId WHERE accountId = :sourceId")
    suspend fun moveAccountId(sourceId: Long, targetId: Long)

    @Query("UPDATE txn SET toAccountId = :targetId WHERE toAccountId = :sourceId")
    suspend fun moveToAccountId(sourceId: Long, targetId: Long)

    @Query("SELECT * FROM txn ORDER BY id")
    suspend fun getAllOnce(): List<TxnEntity>

    @Query("DELETE FROM txn")
    suspend fun deleteAll()
}

@Dao
interface TxnEditLogDao {
    @Insert
    suspend fun insert(log: TxnEditLogEntity)

    @Query("SELECT * FROM txn_edit_log WHERE txnId = :txnId ORDER BY editedAt DESC LIMIT 20")
    fun observeByTxn(txnId: Long): Flow<List<TxnEditLogEntity>>

    @Query("DELETE FROM txn_edit_log WHERE txnId = :txnId")
    suspend fun deleteByTxn(txnId: Long)

    @Query("DELETE FROM txn_edit_log")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget WHERE scope = 'TOTAL' AND period = 'MONTH' ORDER BY startDate DESC LIMIT 1")
    fun observeTotalMonthBudget(): Flow<BudgetEntity?>

    @Query("SELECT * FROM budget WHERE scope = 'TOTAL' AND period = 'MONTH' ORDER BY startDate DESC LIMIT 1")
    suspend fun getTotalMonthBudget(): BudgetEntity?

    @Query("SELECT COUNT(*) FROM budget")
    suspend fun count(): Int

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Insert
    suspend fun insertAll(items: List<BudgetEntity>)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budget ORDER BY id")
    suspend fun getAllOnce(): List<BudgetEntity>

    @Query("DELETE FROM budget")
    suspend fun deleteAll()
}

@Dao
interface NotificationRawDao {
    @Insert
    suspend fun insert(raw: NotificationRawEntity): Long

    @Insert
    suspend fun insertAll(items: List<NotificationRawEntity>)

    @Query("SELECT * FROM notification_raw ORDER BY receivedAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<NotificationRawEntity>>

    @Query(
        "SELECT * FROM notification_raw WHERE packageName = :packageName AND receivedAt >= :since " +
            "ORDER BY receivedAt DESC LIMIT 20"
    )
    suspend fun recentForPackage(packageName: String, since: Long): List<NotificationRawEntity>

    @Query(
        "SELECT * FROM notification_raw WHERE groupKey = :key AND receivedAt >= :since " +
            "ORDER BY receivedAt DESC LIMIT 1"
    )
    suspend fun findRecentByKey(key: String, since: Long): NotificationRawEntity?

    @Query("UPDATE notification_raw SET title = :title, text = :text, receivedAt = :receivedAt WHERE id = :id")
    suspend fun updateContent(id: Long, title: String?, text: String?, receivedAt: Long)

    @Query("SELECT * FROM notification_raw WHERE id = :id")
    suspend fun getById(id: Long): NotificationRawEntity?

    @Query("UPDATE notification_raw SET matchResult = :result, rejectReason = :reason WHERE id = :id")
    suspend fun updateResult(id: Long, result: String, reason: String?)

    @Query(
        "UPDATE notification_raw SET matchResult = :result, rejectReason = :reason, " +
            "aiResponse = :response, aiModel = :model, aiError = :error WHERE id = :id"
    )
    suspend fun updateAiResult(
        id: Long,
        result: String,
        reason: String?,
        response: String?,
        model: String?,
        error: String?
    )

    @Query("DELETE FROM notification_raw WHERE receivedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM notification_raw ORDER BY id")
    suspend fun getAllOnce(): List<NotificationRawEntity>

    @Query("DELETE FROM notification_raw")
    suspend fun deleteAll()
}

@Dao
interface TransactionKeywordDao {
    @Query("SELECT * FROM transaction_keyword ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TransactionKeywordEntity>>

    @Query("SELECT * FROM transaction_keyword WHERE enabled = 1 ORDER BY sortOrder, id")
    suspend fun getEnabled(): List<TransactionKeywordEntity>

    @Query("SELECT COUNT(*) FROM transaction_keyword")
    suspend fun count(): Int

    @Query("SELECT * FROM transaction_keyword ORDER BY sortOrder, id")
    suspend fun getAllOnce(): List<TransactionKeywordEntity>

    @Insert
    suspend fun insertAll(items: List<TransactionKeywordEntity>)

    @Insert
    suspend fun insert(item: TransactionKeywordEntity): Long

    @Query("UPDATE transaction_keyword SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM transaction_keyword WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transaction_keyword")
    suspend fun deleteAll()
}
