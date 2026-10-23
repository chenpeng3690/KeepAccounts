package com.local.notebook.data.importer

import android.content.Context
import android.net.Uri
import com.local.notebook.data.backup.CsvCodec
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.TxnEntity
import com.local.notebook.data.local.TxnRow
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.domain.parser.CategoryGuesser
import com.local.notebook.domain.parser.Direction
import com.local.notebook.domain.parser.DirectionParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportSource(val label: String) {
    WECHAT("微信"),
    ALIPAY("支付宝"),
    UNKNOWN("通用 CSV")
}

data class ImportedRow(
    val occurredAt: Long,
    val type: String,
    val amount: Long,
    val merchant: String?,
    val note: String?,
    val paymentMethod: String?,
    val dedupeKey: String,
    val categoryName: String?
)

data class CsvImportPreview(
    val source: ImportSource,
    val encoding: String,
    val totalRows: Int,
    val importable: List<ImportedRow>,
    val duplicateCount: Int,
    val skippedCount: Int,
    val failedCount: Int
) {
    val previewRows: List<ImportedRow> get() = importable.take(20)
}

data class ImportReport(
    val imported: Int,
    val duplicate: Int,
    val skipped: Int,
    val failed: Int
)

@Singleton
class CsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository
) {
    private val timeFormatters = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy/M/d HH:mm:ss",
        "yyyy/M/d HH:mm",
        "yyyy年M月d日 HH:mm:ss",
        "yyyy年M月d日 HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss"
    ).map { DateTimeFormatter.ofPattern(it, Locale.CHINA) }

    suspend fun parse(uri: Uri, includeClosed: Boolean = false): CsvImportPreview =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                ?: error("无法读取所选文件")
            val (text, encoding) = decode(bytes)
            val rows = CsvCodec.parse(text)
            val headerIndex = rows.indexOfFirst { row ->
                row.any { normalize(it).contains("交易时间") } &&
                    row.any { normalize(it).contains("金额") }
            }
            if (headerIndex < 0) error("没有找到包含「交易时间」和「金额」的表头")
            val header = rows[headerIndex].map(::normalize)

            fun column(vararg keywords: String): Int =
                header.indexOfFirst { cell -> keywords.any { cell.contains(it) } }

            val timeIndex = column("交易时间", "时间")
            val amountIndex = column("金额")
            val typeIndex = column("收/支", "收支")
            val merchantIndex = column("交易对方", "对方")
            val goodsIndex = column("商品")
            val statusIndex = column("当前状态", "交易状态")
            val paymentIndex = column("支付方式", "付款方式", "收/付款方式")
            val orderIndex = column("交易单号", "订单号")
            val categoryIndex = column("交易分类")
            if (timeIndex < 0 || amountIndex < 0) error("表头缺少时间或金额列")

            val source = when {
                column("交易类型") >= 0 || column("支付方式") >= 0 -> ImportSource.WECHAT
                column("交易分类") >= 0 || column("收/付款方式") >= 0 -> ImportSource.ALIPAY
                else -> ImportSource.UNKNOWN
            }

            var totalRows = 0
            var skipped = 0
            var failed = 0
            var duplicates = 0
            val seenKeys = mutableSetOf<String>()
            val importable = mutableListOf<ImportedRow>()

            rows.drop(headerIndex + 1).forEach { row ->
                if (row.all { it.isBlank() }) return@forEach
                totalRows++

                fun value(index: Int): String =
                    if (index in row.indices) row[index].trim() else ""

                val status = value(statusIndex)
                if (!includeClosed && isClosedStatus(status)) {
                    skipped++
                    return@forEach
                }
                val timeText = value(timeIndex).removePrefix("'").trim()
                val occurredAt = parseTime(timeText)
                if (occurredAt == null) {
                    failed++
                    return@forEach
                }
                val amount = parseAmount(value(amountIndex))
                if (amount == null || amount <= 0) {
                    failed++
                    return@forEach
                }
                val merchant = value(merchantIndex).takeIf { it.isNotBlank() }
                val goods = value(goodsIndex).takeIf { it.isNotBlank() }
                val typeRaw = value(typeIndex)
                val type = when {
                    typeRaw.contains("支出") || typeRaw == "支" -> "EXPENSE"
                    typeRaw.contains("收入") || typeRaw == "收" -> "INCOME"
                    else -> "TRANSFER"
                }
                val textForGuess = listOfNotNull(merchant, goods, value(categoryIndex)).joinToString(" ")
                val direction = when (type) {
                    "INCOME" -> Direction.INCOME
                    "EXPENSE" -> Direction.EXPENSE
                    else -> DirectionParser.detect(textForGuess)
                }
                val categoryName = if (type == "TRANSFER") {
                    null
                } else {
                    CategoryGuesser.guess(textForGuess, direction)
                }
                val orderId = value(orderIndex)
                val dedupeKey = if (orderId.isNotBlank()) {
                    hash("order|$orderId")
                } else {
                    hash("${occurredAt / 60_000}|$amount|${merchant.orEmpty()}")
                }
                if (!seenKeys.add(dedupeKey)) {
                    duplicates++
                    return@forEach
                }
                if (repository.countTxnByDedupeKey(dedupeKey) > 0) {
                    duplicates++
                    return@forEach
                }
                importable.add(
                    ImportedRow(
                        occurredAt = occurredAt,
                        type = type,
                        amount = amount,
                        merchant = merchant,
                        note = goods,
                        paymentMethod = value(paymentIndex).takeIf { it.isNotBlank() },
                        dedupeKey = dedupeKey,
                        categoryName = categoryName
                    )
                )
            }

            CsvImportPreview(
                source = source,
                encoding = encoding,
                totalRows = totalRows,
                importable = importable,
                duplicateCount = duplicates,
                skippedCount = skipped,
                failedCount = failed
            )
        }

    suspend fun commit(preview: CsvImportPreview): ImportReport = withContext(Dispatchers.IO) {
        val accounts = repository.accounts.first()
        val categories = repository.categories.first()
        if (accounts.isEmpty()) error("还没有账户，无法导入")

        val accountCache = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()
        val txns = preview.importable.map { row ->
            val accountId = accountCache.getOrPut(row.paymentMethod.orEmpty()) {
                resolveAccount(row.paymentMethod, accounts)
            }
            val categoryId = row.categoryName?.let { name ->
                categories.firstOrNull {
                    it.name == name && it.type == row.type
                }?.id ?: categories.firstOrNull { it.name == name }?.id
            }
            TxnEntity(
                type = row.type,
                amount = row.amount,
                accountId = accountId,
                categoryId = categoryId,
                merchant = row.merchant,
                note = if (row.type == "TRANSFER") {
                    listOfNotNull(row.note, "不计收支").joinToString(" · ")
                } else {
                    row.note
                },
                occurredAt = row.occurredAt,
                createdAt = now,
                updatedAt = now,
                source = "IMPORT",
                status = "NORMAL",
                dedupeKey = row.dedupeKey
            )
        }
        val counts = repository.importTransactions(txns)
        ImportReport(
            imported = counts.imported,
            duplicate = preview.duplicateCount + counts.duplicate,
            skipped = preview.skippedCount,
            failed = preview.failedCount
        )
    }

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        val rows = repository.activeTxns.first()
        val output = context.contentResolver.openOutputStream(uri) ?: error("无法写入文件")
        BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
            writer.write("\uFEFF")
            writer.write(
                "交易时间,类型,分类,交易对方,商品,收/支,金额,支付方式,状态,交易单号\r\n"
            )
            rows.forEach { row ->
                writer.write(row.toExportLine())
                writer.write("\r\n")
            }
        }
    }

    private fun TxnRow.toExportLine(): String {
        val time = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(occurredAt),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val typeLabel = when (type) {
            "INCOME" -> "收入"
            "TRANSFER" -> "转账"
            else -> "支出"
        }
        val directionLabel = when (type) {
            "INCOME" -> "收入"
            "TRANSFER" -> "不计收支"
            else -> "支出"
        }
        val amountText = BigDecimal.valueOf(amount)
            .movePointLeft(2)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
        return listOf(
            time,
            typeLabel,
            categoryName.orEmpty(),
            merchant.orEmpty(),
            note.orEmpty(),
            directionLabel,
            amountText,
            accountName.orEmpty(),
            "正常",
            id.toString()
        ).joinToString(",") { CsvCodec.escape(it) }
    }

    private fun resolveAccount(paymentMethod: String?, accounts: List<AccountEntity>): Long {
        val payment = paymentMethod.orEmpty()
        if (payment.isNotBlank()) {
            accounts.firstOrNull { account ->
                payment.contains(account.name) ||
                    account.name.contains(payment) ||
                    (payment.length >= 2 && account.name.contains(payment.take(2)))
            }?.let { return it.id }
            if (payment.contains("微信")) {
                accounts.firstOrNull { it.name.contains("微信") }?.let { return it.id }
            }
            if (payment.contains("支付宝")) {
                accounts.firstOrNull { it.name.contains("支付宝") }?.let { return it.id }
            }
            if (payment.contains("花呗")) {
                accounts.firstOrNull { it.name.contains("花呗") }?.let { return it.id }
            }
            if (payment.contains("信用")) {
                accounts.firstOrNull {
                    it.name.contains("信用") || it.type == "CREDIT"
                }?.let { return it.id }
            }
            if (payment.contains("招商")) {
                accounts.firstOrNull { it.name.contains("招商") }?.let { return it.id }
            }
        }
        return accounts.first().id
    }

    private fun decode(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8"
        }
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        if (utf8 != null) return utf8 to "UTF-8"

        val gbk = runCatching {
            Charset.forName("GBK").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        if (gbk != null) return gbk to "GBK"
        return String(bytes, Charsets.UTF_8) to "UTF-8"
    }

    private fun parseTime(text: String): Long? {
        if (text.isBlank()) return null
        timeFormatters.forEach { formatter ->
            runCatching {
                return LocalDateTime.parse(text, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        }
        return null
    }

    private fun parseAmount(raw: String): Long? {
        var value = raw
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()
        if (value.isEmpty()) return null
        if (value.startsWith("(") && value.endsWith(")")) {
            value = value.substring(1, value.length - 1)
        }
        value = value.removePrefix("-").removePrefix("+")
        val decimal = value.toBigDecimalOrNull() ?: return null
        return decimal.setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .toLong()
            .takeIf { it > 0 }
    }

    private fun isClosedStatus(status: String): Boolean {
        val closed = listOf("关闭", "已退款", "退款成功", "等待付款", "交易失败", "已撤销", "全额退款")
        return closed.any { status.contains(it) }
    }

    private fun normalize(cell: String): String =
        cell.replace("\uFEFF", "").replace(" ", "").replace("\n", "").trim()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
