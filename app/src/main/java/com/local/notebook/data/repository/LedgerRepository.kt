package com.local.notebook.data.repository

import androidx.room.withTransaction
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.BudgetEntity
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.data.local.LedgerDatabase
import com.local.notebook.data.local.NotificationRawEntity
import com.local.notebook.data.local.PendingRow
import com.local.notebook.data.local.TransactionKeywordEntity
import com.local.notebook.data.local.TxnEditLogEntity
import com.local.notebook.data.local.TxnEntity
import com.local.notebook.data.local.TxnRow
import com.local.notebook.data.repository.SeedContent.categories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerRepository @Inject constructor(
    private val db: LedgerDatabase
) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val txnDao = db.txnDao()
    private val editLogDao = db.txnEditLogDao()
    private val budgetDao = db.budgetDao()
    private val rawDao = db.notificationRawDao()
    private val keywordDao = db.transactionKeywordDao()

    private val seedMutex = Mutex()

    val activeTxns: Flow<List<TxnRow>> = txnDao.observeActiveRows()
    val deletedTxns: Flow<List<TxnRow>> = txnDao.observeDeletedRows()
    val accounts: Flow<List<AccountEntity>> = accountDao.observeActive()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val budget: Flow<BudgetEntity?> = budgetDao.observeTotalMonthBudget()
    val rawLogs: Flow<List<NotificationRawEntity>> = rawDao.observeRecent()
    val keywords: Flow<List<TransactionKeywordEntity>> = keywordDao.observeAll()
    val pendingTxns: Flow<List<PendingRow>> = txnDao.observePendingRows()
    val pendingCount: Flow<Int> = txnDao.observePendingCount()

    suspend fun getTxn(id: Long): TxnEntity? = txnDao.getById(id)

    suspend fun getCategory(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun findRecentRawByKey(key: String, since: Long): NotificationRawEntity? =
        rawDao.findRecentByKey(key, since)

    suspend fun insertRawNotification(raw: NotificationRawEntity): Long = rawDao.insert(raw)

    suspend fun updateRawContent(id: Long, title: String?, text: String?, receivedAt: Long) =
        rawDao.updateContent(id, title, text, receivedAt)

    suspend fun deleteOldRawNotifications(cutoff: Long) = rawDao.deleteOlderThan(cutoff)

    suspend fun countTxnByDedupeKey(key: String): Int = txnDao.countByDedupeKey(key)

    suspend fun categoryIdByName(name: String?, type: String?): Long? {
        if (name == null) return null
        val all = categoryDao.getAll()
        return all.firstOrNull { it.name == name && (type == null || it.type == type) }?.id
            ?: all.firstOrNull { it.name == name }?.id
    }

    suspend fun insertNotificationTransaction(txn: TxnEntity): Long = withContext(Dispatchers.IO) {
        val id = txnDao.insert(txn)
        recalculateBalances()
        id
    }

    suspend fun recentRawForPackage(packageName: String, since: Long): List<NotificationRawEntity> =
        rawDao.recentForPackage(packageName, since)

    suspend fun getRaw(id: Long): NotificationRawEntity? = rawDao.getById(id)

    suspend fun updateRawAiResult(
        id: Long,
        result: String,
        reason: String?,
        response: String?,
        model: String?,
        error: String?
    ) = rawDao.updateAiResult(id, result, reason, response, model, error)

    suspend fun confirmPendingTransaction(id: Long): Boolean = withContext(Dispatchers.IO) {
        val txn = txnDao.getById(id) ?: return@withContext false
        if (txn.status != "PENDING") return@withContext false
        if (txn.type == "TRANSFER" && txn.toAccountId == null) return@withContext false
        val now = System.currentTimeMillis()
        db.withTransaction {
            txnDao.updateStatus(id, "NORMAL", now)
            txn.rawNotificationId?.let { rawId ->
                rawDao.updateResult(rawId, "AI_MATCHED", "用户确认入账")
            }
        }
        recalculateBalances()
        true
    }

    suspend fun rejectPendingTransaction(id: Long) = withContext(Dispatchers.IO) {
        val txn = txnDao.getById(id) ?: return@withContext
        if (txn.status != "PENDING") return@withContext
        db.withTransaction {
            txn.rawNotificationId?.let { rawId ->
                rawDao.updateResult(rawId, "AI_IGNORED", "用户判为噪音")
            }
            txnDao.deleteById(id)
        }
    }

    suspend fun confirmAllPending() = withContext(Dispatchers.IO) {
        val pending = txnDao.getPendingEntities()
        if (pending.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        db.withTransaction {
            pending.forEach { txn ->
                if (txn.type == "TRANSFER" && txn.toAccountId == null) return@forEach
                txnDao.updateStatus(txn.id, "NORMAL", now)
                txn.rawNotificationId?.let { rawId ->
                    rawDao.updateResult(rawId, "AI_MATCHED", "用户确认入账")
                }
            }
        }
        recalculateBalances()
    }

    suspend fun rejectAllPending() = withContext(Dispatchers.IO) {
        val pending = txnDao.getPendingEntities()
        if (pending.isEmpty()) return@withContext
        db.withTransaction {
            pending.forEach { txn ->
                txn.rawNotificationId?.let { rawId ->
                    rawDao.updateResult(rawId, "AI_IGNORED", "用户判为噪音")
                }
                txnDao.deleteById(txn.id)
            }
        }
    }

    suspend fun enabledKeywords(): List<String> =
        keywordDao.getEnabled().map { it.word }

    suspend fun addKeyword(word: String): Long = withContext(Dispatchers.IO) {
        val normalized = word.trim()
        if (normalized.isEmpty()) return@withContext -1L
        val existing = keywordDao.getAllOnce().firstOrNull { it.word == normalized }
        if (existing != null) return@withContext existing.id
        keywordDao.insert(
            TransactionKeywordEntity(
                word = normalized,
                enabled = true,
                isBuiltin = false,
                sortOrder = (keywordDao.getAllOnce().maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
        )
    }

    suspend fun setKeywordEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        keywordDao.setEnabled(id, enabled)
    }

    suspend fun deleteKeyword(id: Long) = withContext(Dispatchers.IO) {
        keywordDao.deleteById(id)
    }

    suspend fun restoreDefaultKeywords() = withContext(Dispatchers.IO) {
        keywordDao.deleteAll()
        keywordDao.insertAll(SeedContent.keywords())
    }

    fun observeEditLogs(txnId: Long): Flow<List<TxnEditLogEntity>> = editLogDao.observeByTxn(txnId)

    fun observeAccount(id: Long): Flow<AccountEntity?> = accountDao.observeById(id)

    fun observeAccountTransactions(accountId: Long): Flow<List<TxnRow>> =
        txnDao.observeRowsForAccount(accountId)

    suspend fun getAccount(id: Long): AccountEntity? = accountDao.getById(id)

    suspend fun addAccount(
        name: String,
        type: String,
        initialBalance: Long,
        creditLimit: Long?,
        billDay: Int?,
        repayDay: Int?,
        includeInNetWorth: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val sortOrder = (accountDao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = accountDao.insert(
            AccountEntity(
                name = name.trim(),
                type = type,
                initialBalance = initialBalance,
                currentBalance = initialBalance,
                creditLimit = creditLimit,
                billDay = billDay,
                repayDay = repayDay,
                includeInNetWorth = includeInNetWorth,
                icon = name.trim().take(1).ifBlank { "账" },
                colorHex = colorForType(type),
                sortOrder = sortOrder
            )
        )
        recalculateBalances()
        id
    }

    suspend fun updateAccount(
        id: Long,
        name: String,
        type: String,
        initialBalance: Long,
        creditLimit: Long?,
        billDay: Int?,
        repayDay: Int?,
        includeInNetWorth: Boolean
    ) = withContext(Dispatchers.IO) {
        val account = accountDao.getById(id) ?: return@withContext
        accountDao.update(
            account.copy(
                name = name.trim(),
                type = type,
                initialBalance = initialBalance,
                creditLimit = creditLimit,
                billDay = billDay,
                repayDay = repayDay,
                includeInNetWorth = includeInNetWorth,
                icon = name.trim().take(1).ifBlank { account.icon },
                colorHex = colorForType(type)
            )
        )
        recalculateBalances()
    }

    suspend fun accountTransactionCount(id: Long): Int = accountDao.activeTxnCount(id)

    suspend fun deleteAccount(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (accountDao.activeTxnCount(id) > 0) return@withContext false
        accountDao.deleteById(id)
        true
    }

    suspend fun moveAccountTransactions(sourceId: Long, targetId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            txnDao.moveAccountId(sourceId, targetId)
            txnDao.moveToAccountId(sourceId, targetId)
        }
        recalculateBalances()
    }

    suspend fun addTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        occurredAt: Long,
        note: String?
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = txnDao.insert(
            TxnEntity(
                type = "TRANSFER",
                amount = amount,
                accountId = fromAccountId,
                toAccountId = toAccountId,
                merchant = "转账",
                note = note?.takeIf { it.isNotBlank() },
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                source = "MANUAL",
                status = "NORMAL"
            )
        )
        recalculateBalances()
        id
    }

    suspend fun calibrateAccount(accountId: Long, targetBalance: Long): Long? =
        withContext(Dispatchers.IO) {
            val account = accountDao.getById(accountId) ?: return@withContext null
            val diff = targetBalance - account.currentBalance
            if (diff == 0L) return@withContext null
            val type = if (diff > 0) "INCOME" else "EXPENSE"
            val amount = kotlin.math.abs(diff)
            val now = System.currentTimeMillis()
            val id = txnDao.insert(
                TxnEntity(
                    type = type,
                    amount = amount,
                    accountId = accountId,
                    categoryId = categoryIdByName("余额调整", null),
                    merchant = "余额调整",
                    note = "校准余额",
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now,
                    source = "ADJUSTMENT",
                    status = "NORMAL"
                )
            )
            recalculateBalances()
            id
        }

    suspend fun setTotalMonthBudget(amount: Long) = withContext(Dispatchers.IO) {
        val existing = budgetDao.getTotalMonthBudget()
        if (existing != null) {
            budgetDao.update(existing.copy(amount = amount))
        } else {
            val monthStart = YearMonth.now()
                .atDay(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            budgetDao.insert(
                BudgetEntity(
                    scope = "TOTAL",
                    period = "MONTH",
                    amount = amount,
                    startDate = monthStart
                )
            )
        }
    }

    data class ImportCounts(val imported: Int, val duplicate: Int)

    suspend fun importTransactions(rows: List<TxnEntity>): ImportCounts = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext ImportCounts(0, 0)
        var imported = 0
        var duplicate = 0
        db.withTransaction {
            rows.forEach { txn ->
                val key = txn.dedupeKey
                val exists = !key.isNullOrBlank() && txnDao.countByDedupeKey(key) > 0
                if (exists) {
                    duplicate++
                } else {
                    txnDao.insert(txn)
                    imported++
                }
            }
        }
        if (imported > 0) recalculateBalances()
        ImportCounts(imported, duplicate)
    }

    suspend fun addTransaction(
        type: String,
        amount: Long,
        accountId: Long,
        categoryId: Long?,
        merchant: String?,
        note: String?,
        occurredAt: Long,
        source: String = "MANUAL"
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = txnDao.insert(
            TxnEntity(
                type = type,
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                merchant = merchant?.takeIf { it.isNotBlank() },
                note = note?.takeIf { it.isNotBlank() },
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                source = source
            )
        )
        recalculateBalances()
        id
    }

    suspend fun updateTransaction(
        id: Long,
        type: String,
        amount: Long,
        accountId: Long,
        categoryId: Long?,
        merchant: String?,
        note: String?,
        occurredAt: Long
    ) = withContext(Dispatchers.IO) {
        val old = txnDao.getById(id) ?: return@withContext
        val now = System.currentTimeMillis()
        val updated = old.copy(
            type = type,
            amount = amount,
            accountId = accountId,
            categoryId = categoryId,
            merchant = merchant?.takeIf { it.isNotBlank() },
            note = note?.takeIf { it.isNotBlank() },
            occurredAt = occurredAt,
            updatedAt = now,
            editCount = old.editCount + 1
        )
        db.withTransaction {
            editLogDao.insert(
                TxnEditLogEntity(
                    txnId = id,
                    beforeJson = old.toJson(),
                    afterJson = updated.toJson(),
                    editedAt = now
                )
            )
            txnDao.update(updated)
        }
        recalculateBalances()
    }

    suspend fun softDelete(id: Long, reason: String? = "用户删除") = withContext(Dispatchers.IO) {
        txnDao.softDelete(id, System.currentTimeMillis(), reason)
        recalculateBalances()
    }

    suspend fun restore(id: Long) = withContext(Dispatchers.IO) {
        txnDao.restore(id, System.currentTimeMillis())
        recalculateBalances()
    }

    suspend fun purge(id: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            editLogDao.deleteByTxn(id)
            txnDao.deleteById(id)
        }
        recalculateBalances()
    }

    suspend fun purgeExpired(retentionDays: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - retentionDays * DAY_MILLIS
        val expired = txnDao.getDeletedBefore(cutoff)
        if (expired.isEmpty()) return@withContext
        db.withTransaction {
            expired.forEach { txn ->
                editLogDao.deleteByTxn(txn.id)
                txnDao.deleteById(txn.id)
            }
        }
        recalculateBalances()
    }

    suspend fun clearTrash() = withContext(Dispatchers.IO) {
        val deleted = txnDao.getDeletedBefore(Long.MAX_VALUE)
        db.withTransaction {
            deleted.forEach { txn ->
                editLogDao.deleteByTxn(txn.id)
                txnDao.deleteById(txn.id)
            }
        }
        recalculateBalances()
    }

    /**
     * 余额始终由「初始余额 + 有效账单」重算，避免增量更新造成的漂移。
     */
    suspend fun recalculateBalances() = withContext(Dispatchers.IO) {
        val txns = txnDao.getActiveForBalance()
        val deltas = HashMap<Long, Long>()
        txns.forEach { txn ->
            when (txn.type) {
                "INCOME" -> deltas[txn.accountId] = (deltas[txn.accountId] ?: 0L) + txn.amount
                "EXPENSE" -> deltas[txn.accountId] = (deltas[txn.accountId] ?: 0L) - txn.amount
                "TRANSFER" -> {
                    txn.toAccountId?.let { target ->
                        deltas[txn.accountId] = (deltas[txn.accountId] ?: 0L) - txn.amount
                        deltas[target] = (deltas[target] ?: 0L) + txn.amount
                    }
                }
            }
        }
        accountDao.getAll().forEach { account ->
            val balance = account.initialBalance + (deltas[account.id] ?: 0L)
            if (account.currentBalance != balance) {
                accountDao.updateBalance(account.id, balance)
            }
        }
    }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            if (categoryDao.count() == 0) {
                categoryDao.insertAll(categories())
            }
            if (budgetDao.count() == 0) {
                val monthStart = YearMonth.now()
                    .atDay(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                budgetDao.insert(
                    BudgetEntity(
                        scope = "TOTAL",
                        period = "MONTH",
                        amount = 600_000L,
                        startDate = monthStart
                    )
                )
            }
            if (keywordDao.count() == 0) {
                keywordDao.insertAll(SeedContent.keywords())
            }
            recalculateBalances()
        }
    }

    private fun TxnEntity.toJson(): String = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("amount", amount)
        put("accountId", accountId)
        put("categoryId", categoryId)
        put("merchant", merchant)
        put("note", note)
        put("occurredAt", occurredAt)
    }.toString()

    private fun colorForType(type: String): String = when (type) {
        "CASH" -> "#067A57"
        "DEBIT" -> "#1668C4"
        "CREDIT" -> "#C92A2A"
        "BNPL" -> "#B85C00"
        "LOAN" -> "#4B4B9E"
        "INVESTMENT" -> "#0CA678"
        else -> "#6B7280"
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}

private object SeedContent {

    fun categories(): List<CategoryEntity> {
        val expense = listOf(
            Triple("餐饮", "餐", "RED"),
            Triple("交通", "交", "BLUE"),
            Triple("购物", "购", "ORANGE"),
            Triple("居住", "住", "PURPLE"),
            Triple("通讯", "通", "BLUE"),
            Triple("娱乐", "娱", "ORANGE"),
            Triple("医疗", "医", "RED"),
            Triple("教育", "教", "BLUE"),
            Triple("人情", "人", "PURPLE"),
            Triple("宠物", "宠", "ORANGE"),
            Triple("旅行", "旅", "GREEN"),
            Triple("数码", "数", "BLUE"),
            Triple("服饰", "服", "PURPLE"),
            Triple("美容", "美", "RED"),
            Triple("运动", "运", "GREEN"),
            Triple("烟酒", "烟", "GRAY"),
            Triple("母婴", "婴", "ORANGE"),
            Triple("保险", "保", "BLUE"),
            Triple("税费", "税", "GRAY"),
            Triple("余额调整", "调", "GRAY"),
            Triple("其他", "其", "GRAY")
        )
        val income = listOf(
            Triple("工资", "工", "GREEN"),
            Triple("奖金", "奖", "ORANGE"),
            Triple("红包", "红", "RED"),
            Triple("报销", "报", "BLUE"),
            Triple("退款", "退", "GREEN"),
            Triple("投资收益", "投", "GREEN"),
            Triple("其他收入", "其", "GRAY")
        )
        return buildList {
            expense.forEachIndexed { index, (name, icon, tone) ->
                add(
                    CategoryEntity(
                        name = name,
                        type = "EXPENSE",
                        icon = icon,
                        tone = tone,
                        sortOrder = index
                    )
                )
            }
            income.forEachIndexed { index, (name, icon, tone) ->
                add(
                    CategoryEntity(
                        name = name,
                        type = "INCOME",
                        icon = icon,
                        tone = tone,
                        sortOrder = index
                    )
                )
            }
        }
    }

    fun keywords(): List<TransactionKeywordEntity> = listOf(
        "支付", "付款", "消费", "扣款", "收款", "到账", "转账", "退款", "交易", "入账",
        "已支付", "支付成功", "付款成功", "扣款成功", "收款成功", "退款成功", "还款", "缴费"
    ).mapIndexed { index, word ->
        TransactionKeywordEntity(
            word = word,
            enabled = true,
            isBuiltin = true,
            sortOrder = index
        )
    }
}
