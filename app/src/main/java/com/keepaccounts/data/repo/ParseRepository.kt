package com.keepaccounts.data.repo

import androidx.room.withTransaction
import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.ParseStatus
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.parse.ParseEngine
import com.keepaccounts.notification.FeedbackNotifier

/**
 * 解析工作流的门面：引擎负责「通知 → 账单」，这里补上待确认箱的人工动作
 * （确认入账 / 修改后入账 / 判为噪音）。
 */
class ParseRepository(
    private val db: AppDatabase,
    private val txnRepo: TransactionRepository,
    settingsRepo: SettingsRepository,
    feedback: FeedbackNotifier? = null
) {

    private val engine = ParseEngine(db, txnRepo, settingsRepo, feedback)

    suspend fun processEvent(eventId: Long) = engine.processEvent(eventId)

    suspend fun processCaptured(): Int = engine.processCaptured()

    /** 确认入账：pending → confirmed，落余额，回写原始通知与规则统计，同一个事务 */
    suspend fun confirmPending(txnId: Long, patch: (TransactionEntity) -> TransactionEntity = { it }) {
        txnRepo.confirmWith(txnId, patch) { next ->
            next.rawEventId?.let { rid ->
                db.rawEventDao().updateResult(rid, ParseStatus.MATCHED, null, next.ruleId, next.id)
            }
            next.ruleId?.let { ruleId ->
                db.ruleDao().recordConfirm(ruleId, System.currentTimeMillis())
            }
        }
    }

    /** 判为噪音：待确认流水从未落账，直接删除，不占用回收站 */
    suspend fun rejectPending(txnId: Long) {
        val txn = txnRepo.findById(txnId) ?: return
        if (txn.status != TxStatus.PENDING) return
        db.withTransaction {
            db.transactionDao().purge(txnId)
            txn.rawEventId?.let { rid ->
                db.rawEventDao().updateResult(rid, ParseStatus.REJECTED, "用户判为噪音", txn.ruleId, null)
            }
            txn.ruleId?.let { ruleId ->
                db.ruleDao().recordReject(ruleId, System.currentTimeMillis())
            }
        }
    }

    /** 批量确认，返回确认条数 */
    suspend fun confirmAll(txnIds: List<Long>): Int {
        var done = 0
        txnIds.forEach { id ->
            runCatching {
                confirmPending(id)
                done++
            }
        }
        return done
    }
}
