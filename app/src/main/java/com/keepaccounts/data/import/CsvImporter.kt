package com.keepaccounts.data.import

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 微信 / 支付宝账单 CSV 解析。
 *
 * 只做「字节 → 结构化行」，不碰数据库、不做账户匹配。要点：
 * - 编码自动探测：支付宝常是 GBK/GB18030，微信是 UTF-8；
 * - 表头嗅探：支付宝前几行是说明文字和 `----` 分隔线，逐行找同时含「交易时间」和「金额」的行；
 * - 来源识别靠表头关键字，不依赖文件名。
 */
object CsvImporter {

    data class RawRow(
        val occurredAt: Long,
        /** expense / income / neutral（不计收支，如零钱充值） */
        val direction: String,
        val amount: Long,
        val merchant: String?,
        val note: String?,
        val categoryHint: String?,
        val paymentMethod: String?,
        val dedupeKey: String?
    )

    data class ParseResult(
        val source: String, // wechat / alipay / unknown
        val rows: List<RawRow>,
        val skippedStatus: Int,
        val skippedNeutral: Int,
        val failed: Int,
        val total: Int
    )

    private const val DIRECTION_EXPENSE = "expense"
    private const val DIRECTION_INCOME = "income"
    private const val DIRECTION_NEUTRAL = "neutral"

    private val SKIP_STATUS = listOf(
        "交易关闭", "已关闭", "已退款", "退款成功", "等待付款", "支付失败", "已取消", "关闭"
    )

    private val timeFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    )

    fun parse(bytes: ByteArray): ParseResult {
        val text = decode(bytes) ?: return ParseResult("unknown", emptyList(), 0, 0, 0, 0)
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }

        val headerIndex = lines.indexOfFirst { it.contains("交易时间") && it.contains("金额") }
        if (headerIndex < 0) return ParseResult("unknown", emptyList(), 0, 0, 0, 0)
        val header = parseCsvLine(lines[headerIndex])

        val source = when {
            header.any { it.contains("交易订单号") || it.contains("收/付款方式") } -> "alipay"
            header.any { it.contains("交易单号") || it.contains("支付方式") } -> "wechat"
            else -> "unknown"
        }

        fun col(vararg names: String): Int =
            header.indexOfFirst { h -> names.any { n -> h.trim().contains(n) } }

        val cTime = col("交易时间")
        val cDirection = col("收/支")
        val cAmount = col("金额")
        val cMerchant = if (source == "alipay") col("交易对方") else col("交易对方")
        val cNote = if (source == "alipay") col("商品说明") else col("商品")
        val cCategory = col("交易分类")
        val cStatus = if (source == "alipay") col("交易状态") else col("当前状态")
        val cMethod = if (source == "alipay") col("收/付款方式") else col("支付方式")
        val cDedupe = if (source == "alipay") col("交易订单号") else col("交易单号")
        val cType = col("交易类型")

        if (cTime < 0 || cAmount < 0) return ParseResult(source, emptyList(), 0, 0, 0, 0)

        val rows = mutableListOf<RawRow>()
        var skippedStatus = 0
        var skippedNeutral = 0
        var failed = 0

        for (i in (headerIndex + 1) until lines.size) {
            val fields = parseCsvLine(lines[i])
            if (fields.size <= maxOf(cTime, cAmount)) { failed++; continue }

            val status = fields.getOrNull(cStatus)?.trim().orEmpty()
            if (status.isNotEmpty() && SKIP_STATUS.any { status.contains(it) }) {
                skippedStatus++
                continue
            }

            val direction = parseDirection(fields.getOrNull(cDirection))
            if (direction == DIRECTION_NEUTRAL) { skippedNeutral++; continue }

            val occurredAt = parseTime(fields[cTime])
            val amount = occurredAt?.let { parseAmount(fields[cAmount]) }
            if (occurredAt == null || amount == null) { failed++; continue }

            val merchant = fields.getOrNull(cMerchant)?.trim()?.takeIf { it.isNotEmpty() && it != "/" }
            val note = fields.getOrNull(cNote)?.trim()?.takeIf { it.isNotEmpty() && it != "/" }
                ?: if (cType >= 0) fields.getOrNull(cType)?.trim()?.takeIf { it.isNotEmpty() && it != "/" } else null
            val categoryHint = fields.getOrNull(cCategory)?.trim()?.takeIf { it.isNotEmpty() }
            val paymentMethod = fields.getOrNull(cMethod)?.trim()?.takeIf { it.isNotEmpty() }
            val dedupeKey = fields.getOrNull(cDedupe)?.trim()?.takeIf { it.isNotEmpty() && it != "/" }

            rows.add(
                RawRow(
                    occurredAt = occurredAt,
                    direction = direction,
                    amount = amount,
                    merchant = merchant,
                    note = note,
                    categoryHint = categoryHint,
                    paymentMethod = paymentMethod,
                    dedupeKey = dedupeKey
                )
            )
        }

        return ParseResult(source, rows, skippedStatus, skippedNeutral, failed, lines.size - headerIndex - 1)
    }

    private fun parseDirection(raw: String?): String {
        val s = raw?.trim().orEmpty()
        return when {
            s == "/" || s.isBlank() || s == "不计收支" -> DIRECTION_NEUTRAL
            s.contains("收入") || s.contains("入账") || s == "收" -> DIRECTION_INCOME
            else -> DIRECTION_EXPENSE
        }
    }

    private fun parseAmount(raw: String): Long? {
        var t = raw.trim().replace("¥", "").replace("￥", "").replace(",", "").replace(" ", "")
        t = t.replace("(", "").replace(")", "").replace("-", "")
        if (t.isEmpty()) return null
        return com.keepaccounts.util.Money.parse(t)
    }

    private fun parseTime(raw: String): Long? {
        val t = raw.trim()
        for (f in timeFormatters) {
            try {
                return LocalDateTime.parse(t, f)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { fields.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }

    private fun decode(bytes: ByteArray): String? {
        // UTF-8 BOM
        if (bytes.size >= 3 && bytes[0].toInt() == 0xEF && bytes[1].toInt() == 0xBB && bytes[2].toInt() == 0xBF) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        // 严格 UTF-8 解码，失败则落到 GB18030
        val utf8 = try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            null
        }
        return utf8 ?: runCatching { String(bytes, Charset.forName("GB18030")) }.getOrNull()
    }
}
