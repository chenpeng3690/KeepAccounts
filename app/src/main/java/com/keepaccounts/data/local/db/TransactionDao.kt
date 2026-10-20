package com.keepaccounts.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 所有列表类查询都带 `deleted_at IS NULL`。
 * 新增查询时请沿用这个前缀，漏一个回收站里的记录就会混进统计。
 */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY occurred_at DESC, id DESC")
    fun observeActive(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL AND occurred_at BETWEEN :from AND :to
        ORDER BY occurred_at DESC, id DESC
        """
    )
    fun observeInRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeTrash(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL AND status = :status ORDER BY occurred_at DESC, id DESC")
    fun observeByStatus(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted_at IS NULL AND status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    /** 只统计 confirmed 的流水；pending 不进余额也不进报表 */
    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM transactions
        WHERE deleted_at IS NULL AND status = 'confirmed'
          AND type = :type AND occurred_at BETWEEN :from AND :to
        """
    )
    fun observeSum(type: String, from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted_at IS NULL")
    suspend fun countActive(): Int

    /** 某个账户的流水：转出算在 account_id，转入算在 to_account_id，两边都要带上 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL AND (account_id = :accountId OR to_account_id = :accountId)
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeForAccount(accountId: Long, limit: Int): Flow<List<TransactionEntity>>

    /**
     * 账户维度的收支合计。
     * 只认 account_id（不含 transfer 的转入端），否则转账会被算成收入。
     */
    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM transactions
        WHERE deleted_at IS NULL AND status = 'confirmed'
          AND account_id = :accountId AND type = :type
          AND occurred_at BETWEEN :from AND :to
        """
    )
    fun observeAccountSum(accountId: Long, type: String, from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted_at IS NULL AND account_id = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun listExpired(before: Long): List<TransactionEntity>

    @Insert
    suspend fun insert(txn: TransactionEntity): Long

    @Update
    suspend fun update(txn: TransactionEntity)

    @Query("UPDATE transactions SET deleted_at = :at, updated_at = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("UPDATE transactions SET deleted_at = NULL, updated_at = :at WHERE id = :id")
    suspend fun restore(id: Long, at: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun purge(id: Long)

    @Query("DELETE FROM transactions WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purgeDeletedBefore(before: Long)
}

@Dao
interface EditLogDao {

    @Insert
    suspend fun insert(log: TransactionEditEntity)

    @Query("SELECT * FROM transaction_edits WHERE txn_id = :txnId ORDER BY edited_at DESC")
    suspend fun listFor(txnId: Long): List<TransactionEditEntity>

    /** 每个 txn 只留最近 20 条，编辑历史是排查用的，不是账本数据 */
    @Query(
        """
        DELETE FROM transaction_edits
        WHERE txn_id = :txnId AND id NOT IN (
            SELECT id FROM transaction_edits WHERE txn_id = :txnId ORDER BY edited_at DESC LIMIT 20
        )
        """
    )
    suspend fun trim(txnId: Long)
}
