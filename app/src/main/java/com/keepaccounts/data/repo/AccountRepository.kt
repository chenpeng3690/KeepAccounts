package com.keepaccounts.data.repo

import androidx.room.withTransaction
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.AccountType
import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.local.db.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 净资产三件套，负债以正数表达（展示时加「欠款」） */
data class NetWorth(
    val net: Long = 0,
    val assets: Long = 0,
    val liabilities: Long = 0
)

/**
 * 账户读写。
 *
 * 余额一致性契约：`accounts.current_balance` 是冗余字段，
 * 任何改动都必须在 `db.withTransaction` 里同时写流水并调用 `applyBalanceDelta`。
 * 单独改余额而不写流水，会让账目与余额永久对不上，而且看不出是哪一笔坏的。
 */
class AccountRepository(private val db: AppDatabase) {

    private val accountDao = db.accountDao()
    private val txnDao = db.transactionDao()

    fun observeActive(): Flow<List<AccountEntity>> = accountDao.observeActive()

    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeNetWorth(): Flow<NetWorth> = accountDao.observeActive().map { list ->
        val included = list.filter { it.includeInNetWorth }
        val assets = included.sumOf { if (it.currentBalance > 0) it.currentBalance else 0L }
        val liabilities = included.sumOf { if (it.currentBalance < 0) -it.currentBalance else 0L }
        NetWorth(net = assets - liabilities, assets = assets, liabilities = liabilities)
    }

    suspend fun findById(id: Long): AccountEntity? = accountDao.findById(id)

    /**
     * 新建账户。
     * @param balance 有符号余额：储蓄卡给正数，信用卡/花呗给**负数**（负余额即欠款）。
     */
    suspend fun create(
        name: String,
        type: String,
        balance: Long,
        cardTail: String? = null,
        creditLimit: Long? = null,
        billDay: Int? = null,
        repayDay: Int? = null,
        includeInNetWorth: Boolean = true,
        color: String = "#3B5BDB",
        icon: String = "wallet"
    ): Long {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            accountDao.insert(
                AccountEntity(
                    name = name,
                    type = type,
                    initialBalance = balance,
                    currentBalance = balance,
                    cardTail = cardTail,
                    creditLimit = creditLimit,
                    billDay = billDay,
                    repayDay = repayDay,
                    includeInNetWorth = includeInNetWorth,
                    icon = icon,
                    color = color,
                    sortOrder = 0,
                    createdAt = now
                )
            )
        }
    }

    suspend fun rename(id: Long, newName: String) {
        val account = accountDao.findById(id) ?: return
        accountDao.update(account.copy(name = newName))
    }

    suspend fun archive(id: Long, archived: Boolean) {
        val account = accountDao.findById(id) ?: return
        accountDao.update(account.copy(archived = archived))
    }

    /**
     * 余额校准：把账户余额改成实际值，差额生成一条 `source=adjustment` 的流水。
     * 这条流水在账单列表里可见（知道钱去哪了），但不计入收支统计。
     */
    suspend fun adjustBalance(accountId: Long, actualBalance: Long): Long? {
        val account = accountDao.findById(accountId) ?: return null
        val delta = actualBalance - account.currentBalance
        if (delta == 0L) return null

        val now = System.currentTimeMillis()
        return db.withTransaction {
            val txnId = txnDao.insert(
                TransactionEntity(
                    type = if (delta > 0) TxType.INCOME else TxType.EXPENSE,
                    amount = if (delta > 0) delta else -delta,
                    occurredAt = now,
                    accountId = accountId,
                    categoryId = null,
                    merchant = null,
                    note = "余额校准",
                    source = TxSource.ADJUSTMENT,
                    status = TxStatus.CONFIRMED,
                    createdAt = now,
                    updatedAt = now
                )
            )
            accountDao.applyBalanceDelta(accountId, delta)
            txnId
        }
    }

    /** 转账：两端账户对冲，不计入收支统计 */
    suspend fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): Long {
        require(amount > 0) { "转账金额必须大于 0" }
        require(fromAccountId != toAccountId) { "转出与转入不能是同一个账户" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val txnId = txnDao.insert(
                TransactionEntity(
                    type = TxType.TRANSFER,
                    amount = amount,
                    occurredAt = occurredAt,
                    accountId = fromAccountId,
                    toAccountId = toAccountId,
                    note = note,
                    source = TxSource.MANUAL,
                    status = TxStatus.CONFIRMED,
                    createdAt = now,
                    updatedAt = now
                )
            )
            accountDao.applyBalanceDelta(fromAccountId, -amount)
            accountDao.applyBalanceDelta(toAccountId, amount)
            txnId
        }
    }

    /** 删除账户前置校验：有未删除流水时不允许删 */
    suspend fun canDelete(id: Long): Boolean {
        val account = accountDao.findById(id) ?: return false
        return account.currentBalance == 0L
    }

    suspend fun delete(id: Long) {
        db.withTransaction { accountDao.deleteById(id) }
    }

    fun isLiability(type: String): Boolean = type in AccountType.LIABILITY
}
