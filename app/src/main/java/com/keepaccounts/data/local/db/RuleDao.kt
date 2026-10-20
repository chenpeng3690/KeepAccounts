package com.keepaccounts.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY builtin DESC, priority ASC, id ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE state = 'active' ORDER BY priority ASC, id ASC")
    suspend fun listActive(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE package_name = :packageName ORDER BY priority ASC, id ASC")
    suspend fun listForPackage(packageName: String): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE package_name IS NULL LIMIT 1")
    suspend fun findGeneric(): RuleEntity?

    @Query("SELECT COUNT(*) FROM rules WHERE builtin = 1")
    suspend fun countBuiltin(): Int

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun findById(id: Long): RuleEntity?

    @Insert
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Query("UPDATE rules SET state = :state, updated_at = :at WHERE id = :id")
    suspend fun setState(id: Long, state: String, at: Long)

    @Query("DELETE FROM rules WHERE id = :id AND builtin = 0")
    suspend fun deleteCustom(id: Long)

    @Query(
        """
        UPDATE rules SET
            hit_count = hit_count + 1,
            last_hit_at = :at,
            updated_at = :at
        WHERE id = :id
        """
    )
    suspend fun recordHit(id: Long, at: Long)

    @Query("UPDATE rules SET confirmed_count = confirmed_count + 1, updated_at = :at WHERE id = :id")
    suspend fun recordConfirm(id: Long, at: Long)

    @Query("UPDATE rules SET rejected_count = rejected_count + 1, updated_at = :at WHERE id = :id")
    suspend fun recordReject(id: Long, at: Long)
}

@Dao
interface RawEventDao {

    @Query("SELECT * FROM raw_events ORDER BY received_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RawEventEntity>>

    @Query("SELECT * FROM raw_events WHERE parse_status = :status ORDER BY received_at DESC LIMIT :limit")
    fun observeByStatus(status: String, limit: Int): Flow<List<RawEventEntity>>

    @Query("SELECT * FROM raw_events WHERE received_at >= :since ORDER BY received_at DESC")
    suspend fun listSince(since: Long): List<RawEventEntity>

    @Query("SELECT COUNT(*) FROM raw_events WHERE parse_status = :status AND received_at >= :since")
    suspend fun countByStatusSince(status: String, since: Long): Int

    /** 同一条通知可能被系统重复投递，用「同包名 + 同标题 + 同正文 + 时间窗」判重 */
    @Query(
        """
        SELECT COUNT(*) FROM raw_events
        WHERE package_name = :packageName AND title = :title AND text = :text
          AND received_at >= :since
        """
    )
    suspend fun countSimilar(packageName: String, title: String?, text: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM raw_events WHERE parse_status = :status")
    fun observeCount(status: String): Flow<Int>

    @Query("SELECT MAX(received_at) FROM raw_events")
    suspend fun lastReceivedAt(): Long?

    @Query("SELECT * FROM raw_events WHERE id = :id")
    suspend fun findById(id: Long): RawEventEntity?

    /** 待确认箱按 rawEventId 反查原文 */
    @Query("SELECT * FROM raw_events WHERE id IN (:ids)")
    suspend fun listByIds(ids: List<Long>): List<RawEventEntity>

    /** 还没解析的采集记录，启动时补跑一遍 */
    @Query("SELECT * FROM raw_events WHERE parse_status = 'captured' ORDER BY received_at ASC LIMIT :limit")
    suspend fun listCaptured(limit: Int): List<RawEventEntity>

    @Insert
    suspend fun insert(event: RawEventEntity): Long

    @Query("UPDATE raw_events SET parse_status = :status, reject_reason = :reason, matched_rule_id = :ruleId, transaction_id = :txnId WHERE id = :id")
    suspend fun updateResult(id: Long, status: String, reason: String?, ruleId: Long?, txnId: Long?)

    @Query("DELETE FROM raw_events WHERE received_at < :before")
    suspend fun purgeBefore(before: Long)
}
