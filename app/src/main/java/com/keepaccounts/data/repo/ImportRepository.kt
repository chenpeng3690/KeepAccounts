package com.keepaccounts.data.repo

import com.keepaccounts.data.import.CsvImporter
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxStatus
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.parse.MerchantClassifier

data class ImportReport(
    val source: String,
    val success: Int,
    val duplicate: Int,
    val skippedStatus: Int,
    val skippedNeutral: Int,
    val failed: Int
)

/**
 * 第三方账单导入：解析 → 分类/账户映射 → 去重 → 分批写库。
 */
class ImportRepository(
    private val db: AppDatabase,
    private val txnRepo: TransactionRepository
) {

    suspend fun import(bytes: ByteArray): ImportReport {
        val parsed = CsvImporter.parse(bytes)
        if (parsed.source == "unknown" || parsed.rows.isEmpty()) {
            return ImportReport(
                parsed.source, 0, 0, parsed.skippedStatus, parsed.skippedNeutral, parsed.failed
            )
        }

        val accounts = db.accountDao().listActive()
        if (accounts.isEmpty()) {
            return ImportReport(parsed.source, 0, 0, parsed.skippedStatus, parsed.skippedNeutral, parsed.failed)
        }
        val categories = db.categoryDao().listAll()
        val defaultAccountId = accounts.first().id

        var success = 0
        var duplicate = 0
        val seenKeys = HashSet<String>()

        for (row in parsed.rows) {
            // 去重：单号优先，无单号用「时间+金额+对方」组合
            val key = row.dedupeKey ?: "${row.occurredAt}|${row.amount}|${row.merchant ?: ""}"
            if (!seenKeys.add(key)) { duplicate++; continue }

            val accountId = matchAccount(row.paymentMethod, accounts) ?: defaultAccountId
            if (db.transactionDao().countSimilar(
                    row.merchant ?: "", row.amount, accountId, row.occurredAt - 60_000
                ) > 0
            ) {
                duplicate++
                continue
            }

            val type = if (row.direction == "income") TxType.INCOME else TxType.EXPENSE
            val categoryId = listOfNotNull(row.categoryHint, row.merchant, row.note)
                .joinToString(" ")
                .let { MerchantClassifier.guessCategory(it, type) }
                ?.let { name -> categories.firstOrNull { it.name == name }?.id }

            txnRepo.add(
                TxnDraft(
                    type = type,
                    amount = row.amount,
                    accountId = accountId,
                    categoryId = categoryId,
                    merchant = row.merchant,
                    note = row.note,
                    source = TxSource.IMPORT,
                    status = TxStatus.CONFIRMED,
                    occurredAt = row.occurredAt
                )
            )
            success++
        }

        return ImportReport(
            parsed.source, success, duplicate, parsed.skippedStatus, parsed.skippedNeutral, parsed.failed
        )
    }

    /** 支付方式 → 已有账户的模糊匹配；匹配不上返回 null（调用方用默认账户） */
    private fun matchAccount(method: String?, accounts: List<AccountEntity>): Long? {
        if (method.isNullOrBlank()) return null
        accounts.forEach { a ->
            if (method.contains(a.name)) return a.id
            if (a.name.length >= 2 && method.contains(a.name.take(2))) return a.id
        }
        return null
    }
}
