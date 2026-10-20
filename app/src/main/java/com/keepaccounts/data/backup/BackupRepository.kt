package com.keepaccounts.data.backup

import androidx.room.withTransaction
import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.AppSettingEntity
import com.keepaccounts.data.local.db.SettingKeys
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.local.db.TransactionEntity

enum class RestoreMode {
    /** 清空现有数据后导入，最干净，但会丢掉备份之后新增的记录 */
    OVERWRITE,
    /** 保留现有数据，按名称匹配账户与分类，重复账单跳过 */
    MERGE
}

data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val accountCount: Int,
    val categoryCount: Int,
    val transactionCount: Int,
    val ruleCount: Int,
    val encrypted: Boolean
)

data class RestoreReport(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val skipped: Int
)

class BackupRepository(
    private val db: AppDatabase,
    private val appVersion: String = "0.1.0"
) {

    private val backupDao = db.backupAccessDao()
    private val accountDao = db.accountDao()

    /** 导出成字节，由调用方决定写到哪（SAF 或私有目录） */
    suspend fun export(password: String?): ByteArray {
        val payload = BackupPayload(
            appVersion = appVersion,
            accounts = backupDao.allAccounts(),
            categories = backupDao.allCategories(),
            transactions = backupDao.allTransactions(),
            rules = backupDao.allRules(),
            budgets = backupDao.allBudgets(),
            settings = backupDao.allSettings()
        )
        return BackupContainer.pack(payload.toJson(), password)
    }

    /** 读元信息用于预览。加密文件必须先给对口令 */
    fun peek(bytes: ByteArray, password: String?): BackupPreview {
        val payload = parse(bytes, password)
        return BackupPreview(
            createdAt = payload.createdAt,
            appVersion = payload.appVersion,
            schemaVersion = payload.schemaVersion,
            accountCount = payload.accounts.size,
            categoryCount = payload.categories.size,
            transactionCount = payload.transactions.size,
            ruleCount = payload.rules.size,
            encrypted = BackupContainer.isEncrypted(bytes)
        )
    }

    fun isEncrypted(bytes: ByteArray): Boolean = BackupContainer.isEncrypted(bytes)

    suspend fun restore(bytes: ByteArray, password: String?, mode: RestoreMode): RestoreReport {
        val payload = parse(bytes, password)
        return db.withTransaction {
            when (mode) {
                RestoreMode.OVERWRITE -> restoreOverwrite(payload)
                RestoreMode.MERGE -> restoreMerge(payload)
            }
        }
    }

    private suspend fun restoreOverwrite(payload: BackupPayload): RestoreReport {
        // 清空顺序：子表先走，否则外键 RESTRICT 会拦下来
        backupDao.clearEditLogs()
        backupDao.clearTransactions()
        backupDao.clearRawEvents()
        backupDao.clearRules()
        backupDao.clearBudgets()
        backupDao.clearCategories()
        backupDao.clearAccounts()
        backupDao.clearSettings()

        backupDao.insertAccounts(payload.accounts)
        backupDao.insertCategories(payload.categories)
        // 先落一张 refund_of 为空的表，再补写自引用，避免外键顺序问题
        backupDao.insertTransactions(payload.transactions.map { it.copy(refundOf = null) })
        payload.transactions.filter { it.refundOf != null }.forEach { txn ->
            runCatching { backupDao.setRefundOf(txn.id, txn.refundOf) }
        }
        backupDao.insertRules(payload.rules)
        backupDao.insertBudgets(payload.budgets)
        backupDao.insertSettings(payload.settings)

        ensureSeededFlag()
        recomputeBalances()
        return RestoreReport(
            accounts = payload.accounts.size,
            categories = payload.categories.size,
            transactions = payload.transactions.size,
            skipped = 0
        )
    }

    private suspend fun restoreMerge(payload: BackupPayload): RestoreReport {
        val accountMap = HashMap<Long, Long>()
        var addedAccounts = 0
        val localAccounts = backupDao.allAccounts().associateBy { it.name }
        payload.accounts.forEach { incoming ->
            val existing = localAccounts[incoming.name]
            if (existing != null) {
                accountMap[incoming.id] = existing.id
            } else {
                val newId = accountDao.insert(incoming.copy(id = 0))
                accountMap[incoming.id] = newId
                addedAccounts++
            }
        }

        val categoryMap = HashMap<Long, Long>()
        var addedCategories = 0
        val localCategories = backupDao.allCategories().associateBy { it.name to it.type }
        payload.categories.forEach { incoming ->
            val existing = localCategories[incoming.name to incoming.type]
            if (existing != null) {
                categoryMap[incoming.id] = existing.id
            } else {
                val newId = db.categoryDao().insert(incoming.copy(id = 0))
                categoryMap[incoming.id] = newId
                addedCategories++
            }
        }

        // 用「时间+金额+类型+账户」做去重键。交易单号在通知来源里没有，只能退而求其次
        val existingKeys = backupDao.allTransactions()
            .map { keyOf(it.occurredAt, it.amount, it.type, it.accountId) }
            .toHashSet()

        var addedTxns = 0
        var skipped = 0
        payload.transactions.forEach { incoming ->
            val accountId = accountMap[incoming.accountId]
            if (accountId == null) {
                skipped++
                return@forEach
            }
            val key = keyOf(incoming.occurredAt, incoming.amount, incoming.type, accountId)
            if (key in existingKeys) {
                skipped++
                return@forEach
            }
            existingKeys.add(key)
            backupDao.insertTransactions(
                listOf(
                    incoming.copy(
                        id = 0,
                        accountId = accountId,
                        toAccountId = incoming.toAccountId?.let { accountMap[it] },
                        categoryId = incoming.categoryId?.let { categoryMap[it] }
                    )
                )
            )
            addedTxns++
        }

        recomputeBalances()
        return RestoreReport(
            accounts = addedAccounts,
            categories = addedCategories,
            transactions = addedTxns,
            skipped = skipped
        )
    }

    private fun keyOf(occurredAt: Long, amount: Long, type: String, accountId: Long) =
        "$occurredAt|$amount|$type|$accountId"

    private suspend fun ensureSeededFlag() {
        val existing = db.settingsDao().get(SettingKeys.SEEDED)
        if (existing == null) {
            db.settingsDao().put(
                AppSettingEntity(SettingKeys.SEEDED, System.currentTimeMillis().toString())
            )
        }
    }

    /**
     * 按流水重算所有账户余额。
     *
     * 备份恢复后必须跑一遍：合并模式会重建账户 id，覆盖模式可能带进旧版本的不一致数据。
     * 余额 = 初始余额 + Σ流水影响，这条不变式比「相信存档里的 currentBalance」可靠。
     */
    suspend fun recomputeBalances() {
        val accounts = backupDao.allAccounts()
        val delta = HashMap<Long, Long>(accounts.size)
        accounts.forEach { delta[it.id] = 0L }

        backupDao.allTransactions()
            .filter { it.deletedAt == null && it.status == TxStatus.CONFIRMED }
            .forEach { txn ->
                when (txn.type) {
                    TxType.EXPENSE -> delta[txn.accountId] = (delta[txn.accountId] ?: 0) - txn.amount
                    TxType.INCOME, TxType.REFUND ->
                        delta[txn.accountId] = (delta[txn.accountId] ?: 0) + txn.amount
                    TxType.TRANSFER -> {
                        delta[txn.accountId] = (delta[txn.accountId] ?: 0) - txn.amount
                        txn.toAccountId?.let { to ->
                            delta[to] = (delta[to] ?: 0) + txn.amount
                        }
                    }
                }
            }

        accounts.forEach { account ->
            val expected = account.initialBalance + (delta[account.id] ?: 0L)
            if (expected != account.currentBalance) {
                accountDao.update(account.copy(currentBalance = expected))
            }
        }
    }

    private fun parse(bytes: ByteArray, password: String?): BackupPayload {
        val json = BackupContainer.unpack(bytes, password)
        return BackupPayload.fromJson(json)
    }
}
