package com.keepaccounts.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额工具。内部一律用「分」的 Long，绝不用 Double。
 *
 * 只在两个地方做转换：用户输入时 parse 成 Long，渲染时 format 成字符串。
 */
object Money {

    /** 匹配「¥25.00」「￥25」「25.00元」三种常见写法 */
    private val AMOUNT_PATTERN = Regex("[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?)|(\\d+(?:\\.\\d{1,2})?)\\s*元")

    /** 128640 -> "1,286.40" */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val yuan = abs / 100
        val fen = abs % 100
        val yuanText = yuan.toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        val sign = if (negative) "-" else ""
        return "$sign$yuanText.${fen.toString().padStart(2, '0')}"
    }

    /** 带符号的展示金额："-25.00" / "+12,400.00" */
    fun formatWithSign(cents: Long, positive: Boolean): String {
        val sign = if (positive) "+" else "-"
        return sign + format(if (cents < 0) -cents else cents)
    }

    /** "12.5" / "1,286.40" / "¥25" -> 分。无法解析返回 null */
    fun parse(input: String): Long? {
        val cleaned = input.trim()
            .replace(",", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** 从一段文本里抽出第一个带货币标记的金额（分），用于通知解析。没有则返回 null */
    fun extractFirstAmount(text: String): Long? {
        val match = AMOUNT_PATTERN.find(text) ?: return null
        val raw = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
        return parse(raw)
    }
}
