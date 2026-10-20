package com.keepaccounts.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE enabled = 1")
    fun observeEnabled(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE enabled = 1")
    suspend fun listEnabled(): List<BudgetEntity>

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE budgets SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

@Dao
interface BackupDao {

    @Query("SELECT * FROM backup_history ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history ORDER BY created_at DESC")
    suspend fun listAll(): List<BackupHistoryEntity>

    @Query("SELECT * FROM backup_history WHERE trigger = :trigger ORDER BY created_at DESC")
    suspend fun listByTrigger(trigger: String): List<BackupHistoryEntity>

    @Insert
    suspend fun insert(record: BackupHistoryEntity): Long

    @Query("DELETE FROM backup_history WHERE id = :id")
    suspend fun delete(id: Long)
}
