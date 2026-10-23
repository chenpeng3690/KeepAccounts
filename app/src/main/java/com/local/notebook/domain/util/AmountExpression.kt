package com.local.notebook.domain.util

import java.math.BigDecimal
import java.math.RoundingMode

/** 支持 + - × ÷ 的简单金额算式解析，供手动记账键盘使用。 */
object AmountExpression {

    fun evaluate(input: String): BigDecimal? {
        val normalized = input
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")
        if (normalized.isEmpty()) return null
        val parser = Parser(normalized)
        val value = parser.parseExpression() ?: return null
        if (parser.position != normalized.length) return null
        if (value < BigDecimal.ZERO) return null
        return value
    }

    fun toCents(value: BigDecimal): Long =
        value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

    private class Parser(private val text: String) {
        var position: Int = 0

        fun parseExpression(): BigDecimal? {
            var value = parseTerm() ?: return null
            while (position < text.length) {
                val op = text[position]
                if (op != '+' && op != '-') break
                position++
                val right = parseTerm() ?: return null
                value = if (op == '+') value + right else value - right
            }
            return value
        }

        private fun parseTerm(): BigDecimal? {
            var value = parseFactor() ?: return null
            while (position < text.length) {
                val op = text[position]
                if (op != '*' && op != '/') break
                position++
                val right = parseFactor() ?: return null
                value = if (op == '*') {
                    value * right
                } else {
                    if (right.compareTo(BigDecimal.ZERO) == 0) return null
                    value.divide(right, 10, RoundingMode.HALF_UP)
                }
            }
            return value
        }

        private fun parseFactor(): BigDecimal? {
            val start = position
            while (position < text.length && (text[position].isDigit() || text[position] == '.')) {
                position++
            }
            if (start == position) return null
            val raw = text.substring(start, position)
            return raw.toBigDecimalOrNull()
        }
    }
}
