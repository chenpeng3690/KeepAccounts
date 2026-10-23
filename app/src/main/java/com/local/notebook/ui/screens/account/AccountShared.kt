package com.local.notebook.ui.screens.account

fun accountTypeLabel(type: String): String = when (type) {
    "CASH" -> "现金"
    "DEBIT" -> "储蓄卡"
    "CREDIT" -> "信用卡"
    "BNPL" -> "花呗 / 白条"
    "LOAN" -> "借呗 / 网贷"
    "INVESTMENT" -> "投资账户"
    else -> "其他"
}

fun isLiabilityType(type: String): Boolean =
    type == "CREDIT" || type == "BNPL" || type == "LOAN"

