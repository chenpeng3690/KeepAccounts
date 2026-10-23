package com.local.notebook.ui.util

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val moneyFormat = DecimalFormat(
    "#,##0.00",
    DecimalFormatSymbols(Locale.US)
).apply {
    isGroupingUsed = true
}

/** 以「分」为单位格式化为带千分位的两位小数。 */
fun formatMoney(cents: Long): String =
    moneyFormat.format(BigDecimal.valueOf(cents).movePointLeft(2))

/** 以「分」为单位格式化为整数或两位小数（无币种符号）。 */
fun formatMoneyShort(cents: Long): String {
    val yuan = BigDecimal.valueOf(cents).movePointLeft(2)
    return if (cents % 100 == 0L) {
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(yuan)
    } else {
        moneyFormat.format(yuan)
    }
}

private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val shortDateFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(clockFormatter)

/** 「今天 09:38」「昨天 21:10」「10月19日 09:38」。 */
fun formatDateTimeLabel(millis: Long): String {
    val date = millisToLocalDate(millis)
    val today = LocalDate.now()
    val clock = formatClock(millis)
    return when (date) {
        today -> "今天 $clock"
        today.minusDays(1) -> "昨天 $clock"
        else -> "${date.format(monthDayFormatter)} $clock"
    }
}

/** 「10-19」。 */
fun formatShortDate(millis: Long): String = millisToLocalDate(millis).format(shortDateFormatter)

/** 「2026年10月19日 09:38」。 */
fun formatFullDateTime(millis: Long): String {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return "${dateTime.format(fullDateFormatter)} ${dateTime.format(clockFormatter)}"
}

