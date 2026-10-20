package com.keepaccounts.ui

import com.keepaccounts.data.local.db.AccountType

/**
 * 账户类型的中文名与展示规则。
 * 类型值存库里是小写字符串，界面上永远走这里取名字，不要在 UI 里写 when 分支。
 */
object AccountLabels {

    /** 新建账户时的类型选项顺序 */
    val typeOptions: List<String> = listOf(
        AccountType.CASH,
        AccountType.DEBIT,
        AccountType.CREDIT,
        AccountType.BNPL,
        AccountType.LOAN,
        AccountType.INVESTMENT,
        AccountType.OTHER
    )

    fun typeName(type: String): String = when (type) {
        AccountType.CASH -> "现金"
        AccountType.DEBIT -> "储蓄卡"
        AccountType.CREDIT -> "信用卡"
        AccountType.BNPL -> "花呗 / 白条"
        AccountType.LOAN -> "借呗 / 网贷"
        AccountType.INVESTMENT -> "投资账户"
        else -> "其他"
    }

    /** 负债类账户的余额含义是「欠款」，界面上要换词，否则用户看到负号会误解 */
    fun isLiability(type: String): Boolean = type in AccountType.LIABILITY

    /** 余额标签：资产类是「余额」，负债类是「欠款」 */
    fun balanceLabel(type: String): String =
        if (isLiability(type)) "当前欠款" else "当前余额"

    /** 新建时输入框的标签 */
    fun initialBalanceLabel(type: String): String =
        if (isLiability(type)) "当前欠款" else "初始余额"

    /** 这类账户是否需要额度 / 账单日 / 还款日 */
    fun hasCreditFields(type: String): Boolean =
        type == AccountType.CREDIT || type == AccountType.BNPL
}
