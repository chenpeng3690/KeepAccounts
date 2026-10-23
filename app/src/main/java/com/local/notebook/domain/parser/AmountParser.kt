package com.local.notebook.domain.parser

import java.math.RoundingMode

/**
 * 金额抽取。优先带货币符号的金额，其次「元/块」；
 * 营销文案里的「领/抢/送 N 元红包」会被降权，不会被当成交易金额。
 */
object AmountParser {

    private val currencyPattern = Regex("""[¥￥]\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""")
    private val yuanPattern = Regex("""([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|块)""")
    private val rmbPattern = Regex("""(?i)RMB\s*([0-9]+(?:\.[0-9]{1,2})?)""")

    private val marketingWords = listOf("领", "抢", "送", "优惠", "红包", "立减", "券", "折扣", "省", "满")
    private val transactionWords = listOf(
        "支付", "付款", "消费", "扣款", "收款", "到账", "退款", "转账", "还款", "缴费", "已付"
    )
    private val legitimateIncomeWords = listOf("到账", "收款", "退款", "入账", "支付成功", "付款成功")

    fun bestAmount(text: String): ParsedAmount? {
        val candidates = findCandidates(text)
        val valid = candidates.filter { it.cents in 1..MAX_AMOUNT_CENTS }
        val best = valid.maxByOrNull { it.score } ?: return null
        return best.takeIf { it.score >= 2 }
    }

    fun findAllAmounts(text: String): List<ParsedAmount> =
        findCandidates(text).filter { it.cents > 0 }

    private fun findCandidates(text: String): List<ParsedAmount> {
        val result = mutableListOf<ParsedAmount>()

        currencyPattern.findAll(text).forEach { match ->
            addCandidate(result, text, match, baseScore = 4)
        }
        yuanPattern.findAll(text).forEach { match ->
            addCandidate(result, text, match, baseScore = 3)
        }
        rmbPattern.findAll(text).forEach { match ->
            addCandidate(result, text, match, baseScore = 4)
        }
        return result.distinctBy { it.cents to it.raw }
    }

    private fun addCandidate(
        target: MutableList<ParsedAmount>,
        text: String,
        match: MatchResult,
        baseScore: Int
    ) {
        val raw = match.groupValues.getOrNull(1)?.replace(",", "") ?: return
        val value = raw.toBigDecimalOrNull() ?: return
        val cents = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

        val start = match.range.first
        val before = text.substring(maxOf(0, start - 8), start)
        val after = text.substring(match.range.last + 1, minOf(text.length, match.range.last + 15))
        val around = before + after

        var score = baseScore
        val legitimateIncome = legitimateIncomeWords.any { around.contains(it) }
        if (!legitimateIncome && marketingWords.any { before.contains(it) }) score -= 6
        if (transactionWords.any { around.contains(it) }) score += 2
        if (raw.length > 7) score -= 8

        target.add(ParsedAmount(cents, raw, score))
    }

    const val MAX_AMOUNT_CENTS = 10_000_000L
}

/** 方向识别：收入词优先，涉及退款时按收入处理。 */
object DirectionParser {

    private val incomeWords = listOf(
        "收款", "到账", "收入", "退款", "转入", "已收", "入账", "红包到账", "返现", "收益"
    )
    private val expenseWords = listOf(
        "支付", "付款", "消费", "扣款", "转出", "支出", "已付", "已扣", "缴费", "还款"
    )

    fun detect(text: String): Direction {
        if (incomeWords.any { text.contains(it) }) return Direction.INCOME
        if (expenseWords.any { text.contains(it) }) return Direction.EXPENSE
        return Direction.UNKNOWN
    }
}
