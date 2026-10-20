package com.keepaccounts.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort_order, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sort_order, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE archived = 0")
    suspend fun countActive(): Int

    /** 一次性读全部活跃账户，解析引擎用来找默认账户 */
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort_order, id")
    suspend fun listActive(): List<AccountEntity>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 余额的唯一写入口。调用方必须在 `db.withTransaction` 内，
     * 且同事务里已写入对应流水，否则余额会跟流水对不上。
     */
    @Query("UPDATE accounts SET current_balance = current_balance + :delta WHERE id = :id")
    suspend fun applyBalanceDelta(id: Long, delta: Long)
}
