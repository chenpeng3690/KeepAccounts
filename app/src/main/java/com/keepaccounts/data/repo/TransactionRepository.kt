package com.keepaccounts.data.repo

import androidx.room.withTransaction
import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.Confidence
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.local.db.TransactionEditEntity
import com.keepaccounts.data.local.db.TransactionEntity
import kotlinx.coroutines.flow.Flow
import com.keepaccounts.util.Time

/** 新建账单时的入参，避免 add() 长成一串同名参数 */
data class TxnDraft(
    val type: String,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val merchant: String? = null,
    val note: String? = null,
    val source: String = TxSource.MANUAL,
    val status: String = TxStatus.CONFIRMED,
    val confidence: String = Confidence.HIGH,
    val occurredAt: Long = System.currentTimeMillis(),
    val refundOf: Long? = null,
    val rawEventId: Long? = null,
    val ruleId: Long? = null
)

/**
 * 账单读写 + 余额联动。
 *
 * 余额改动只发生在写方法内部，且都在事务里成对出现：写流水 → 改余额。
 * 删除是软删除并**立即回滚余额**，所以回收站里的账单不影响任何数字。
 */
class TransactionRepository(private val db: AppDatabase) {

    private val txnDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val editLogDao = db.editLogDao()

    fun observeActive(): Flow<List<TransactionEntity>> = txnDao.observeActive()

    fun observeInRange(from: Long, to: Long): Flow<List<TransactionEntity>> =
        txnDao.observeInRange(from, to)

    fun observeTrash(): Flow<List<TransactionEntity>> = txnDao.observeTrash()

    fun observePending(): Flow<List<TransactionEntity>> =
        txnDao.observeByStatus(TxStatus.PENDING)

    fun observePendingCount(): Flow<Int> =
        txnDao.observeCountByStatus(TxStatus.PENDING)

    fun observeForAccount(accountId: Long, limit: Int = 20): Flow<List<TransactionEntity>> =
        txnDao.observeForAccount(accountId, limit)

    fun observeAccountMonthExpense(accountId: Long, ts: Long = System.currentTimeMillis()): Flow<Long> =
        txnDao.observeAccountSum(accountId, TxType.EXPENSE, Time.startOfMonth(ts), Time.endOfMonth(ts))

    fun observeAccountMonthIncome(accountId: Long, ts: Long = System.currentTimeMillis()): Flow<Long> =
        txnDao.observeAccountSum(accountId, TxType.INCOME, Time.startOfMonth(ts), Time.endOfMonth(ts))

    fun observeMonthExpense(ts: Long = System.currentTimeMillis()): Flow<Long> =
        txnDao.observeSum(TxType.EXPENSE, Time.startOfMonth(ts), Time.endOfMonth(ts))

    fun observeMonthIncome(ts: Long = System.currentTimeMillis()): Flow<Long> =
        txnDao.observeSum(TxType.INCOME, Time.startOfMonth(ts), Time.endOfMonth(ts))

    suspend fun findById(id: Long): TransactionEntity? = txnDao.findById(id)

    suspend fun add(draft: TxnDraft): Long =
        db.withTransaction { insertWithinTransaction(draft) }

    /**
     * 在**已有事务**里写流水并应用余额。
     * 给解析引擎用：它需要「写流水 + 回写原始通知 + 记规则命中」落在一个事务里。
     * 调用方必须已处于 [db.withTransaction] 之内。
     */
    suspend fun insertWithinTransaction(draft: TxnDraft): Long {
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            type = draft.type,
            amount = draft.amount,
            occurredAt = draft.occurredAt,
            accountId = draft.accountId,
            toAccountId = draft.toAccountId,
            categoryId = draft.categoryId,
            merchant = draft.merchant,
            note = draft.note,
            source = draft.source,
            confidence = draft.confidence,
            status = draft.status,
            refundOf = draft.refundOf,
            rawEventId = draft.rawEventId,
            ruleId = draft.ruleId,
            createdAt = now,
            updatedAt = now
        )
        val id = txnDao.insert(entity)
        // 待确认的账单不进余额，等用户确认后再落账
        if (entity.status == TxStatus.CONFIRMED) {
            applyEffect(entity, sign = 1)
        }
        return id
    }

    /** 编辑账单：先回滚旧值再套用新值，全程一个事务 */
    suspend fun update(updated: TransactionEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val old = txnDao.findById(updated.id) ?: return@withTransaction
            if (old.status == TxStatus.CONFIRMED) applyEffect(old, sign = -1)

            txnDao.update(updated.copy(updatedAt = now))

            if (updated.status == TxStatus.CONFIRMED) applyEffect(updated, sign = 1)

            editLogDao.insert(
                TransactionEditEntity(
                    txnId = updated.id,
                    beforeJson = old.toSnapshot(),
                    afterJson = updated.toSnapshot(),
                    editedAt = now
                )
            )
            editLogDao.trim(updated.id)
        }
    }

    /** 待确认 -> 正式入账，此时才动余额 */
    suspend fun confirm(id: Long, patch: (TransactionEntity) -> TransactionEntity = { it }) =
        confirmWith(id, patch)

    /**
     * 同 [confirm]，但带一个事务内回调，供解析引擎在落账后回写原始通知与规则统计。
     * 回调在余额已更新、事务尚未提交时执行，保证三处写要么全成要么全不。
     */
    suspend fun confirmWith(
        id: Long,
        patch: (TransactionEntity) -> TransactionEntity = { it },
        after: suspend (TransactionEntity) -> Unit = {}
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val old = txnDao.findById(id) ?: return@withTransaction
            if (old.status == TxStatus.CONFIRMED) return@withTransaction
            val next = patch(old).copy(status = TxStatus.CONFIRMED, updatedAt = now)
            txnDao.update(next)
            applyEffect(next, sign = 1)
            after(next)
        }
    }

    suspend fun softDelete(id: Long) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val txn = txnDao.findById(id) ?: return@withTransaction
            if (txn.deletedAt != null) return@withTransaction
            if (txn.status == TxStatus.CONFIRMED) applyEffect(txn, sign = -1)
            txnDao.softDelete(id, now)
        }
    }

    suspend fun restore(id: Long) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val txn = txnDao.findById(id) ?: return@withTransaction
            if (txn.deletedAt == null) return@withTransaction
            txnDao.restore(id, now)
            if (txn.status == TxStatus.CONFIRMED) applyEffect(txn, sign = 1)
        }
    }

    suspend fun purge(id: Long) {
        db.withTransaction { txnDao.purge(id) }
    }

    /** 清理到期回收站数据，返回清理条数 */
    suspend fun purgeExpired(retentionDays: Int): Int {
        val deadline = Time.daysAgo(System.currentTimeMillis(), retentionDays)
        return db.withTransaction {
            val expired = txnDao.listExpired(deadline)
            txnDao.purgeDeletedBefore(deadline)
            expired.size
        }
    }

    private suspend fun applyEffect(txn: TransactionEntity, sign: Int) {
        when (txn.type) {
            TxType.EXPENSE -> accountDao.applyBalanceDelta(txn.accountId, -txn.amount * sign)
            TxType.INCOME, TxType.REFUND -> accountDao.applyBalanceDelta(txn.accountId, txn.amount * sign)
            TxType.TRANSFER -> {
                accountDao.applyBalanceDelta(txn.accountId, -txn.amount * sign)
                txn.toAccountId?.let { accountDao.applyBalanceDelta(it, txn.amount * sign) }
            }
        }
    }

    private fun TransactionEntity.toSnapshot(): String =
        "type=$type;amount=$amount;account=$accountId;to=$toAccountId;" +
            "category=$categoryId;merchant=$merchant;note=$note;occurred=$occurredAt"
}
