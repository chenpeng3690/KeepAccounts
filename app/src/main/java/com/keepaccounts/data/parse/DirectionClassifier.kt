package com.keepaccounts.data.parse

import com.keepaccounts.data.local.db.TxType

/**
 * 用方向词表判定一笔通知是支出还是收入。
 *
 * 冲突规则：同时出现支出词与收入词（如「退款」+「支付」）时，以「收入/退款」优先，
 * 因为退款、收益、返现这类词几乎可以确定是钱进来。
 */
object DirectionClassifier {

    private val INCOME = listOf(
        "收款", "到账", "收入", "退款", "转入", "已收", "入账", "返现", "收益", "报销", "红包"
    )

    private val EXPENSE = listOf(
        "支付", "付款", "消费", "扣款", "转出", "支出", "已付", "已扣", "缴费", "购买", "还款"
    )

    /** 冲突时优先判收入的强收入词 */
    private val INCOME_STRONG = listOf("退款", "收益", "返现", "报销", "红包", "收款")

    /** 返回 [TxType.EXPENSE] / [TxType.INCOME] / null（无法判定） */
    fun classify(text: String): String? {
        val hasIncome = INCOME.any { text.contains(it) }
        val hasExpense = EXPENSE.any { text.contains(it) }
        return when {
            hasIncome && hasExpense ->
                if (INCOME_STRONG.any { text.contains(it) }) TxType.INCOME else TxType.EXPENSE
            hasIncome -> TxType.INCOME
            hasExpense -> TxType.EXPENSE
            else -> null
        }
    }
}
