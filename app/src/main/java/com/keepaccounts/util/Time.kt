package com.keepaccounts.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间工具。minSdk 29 已可直接用 java.time，不需要 desugaring。
 * 所有对外接口都是毫秒时间戳，避免把 LocalDate 漏到业务层。
 */
object Time {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthDayFmt = DateTimeFormatter.ofPattern("M 月 d 日")
    private val monthFmt = DateTimeFormatter.ofPattern("yyyy 年 M 月")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun toLocal(ts: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)

    fun toDate(ts: Long): LocalDate = toLocal(ts).toLocalDate()

    fun fromDate(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfDay(ts: Long): Long = fromDate(toDate(ts))

    fun endOfDay(ts: Long): Long = startOfDay(ts) + 24 * 60 * 60 * 1000 - 1

    fun startOfMonth(ts: Long): Long {
        val d = toDate(ts).withDayOfMonth(1)
        return fromDate(d)
    }

    fun endOfMonth(ts: Long): Long {
        val d = toDate(ts).withDayOfMonth(1).plusMonths(1)
        return fromDate(d) - 1
    }

    fun daysAgo(now: Long, days: Int): Long = now - days.toLong() * 24 * 60 * 60 * 1000

    fun formatDate(ts: Long): String = toLocal(ts).format(dateFmt)

    fun formatMonthDay(ts: Long): String = toLocal(ts).format(monthDayFmt)

    fun formatMonth(ts: Long): String = toLocal(ts).format(monthFmt)

    fun formatTime(ts: Long): String = toLocal(ts).format(timeFmt)

    fun formatDateTime(ts: Long): String = "${formatDate(ts)} ${formatTime(ts)}"

    /** 列表分组标题：今天 / 昨天 / M 月 d 日 */
    fun dayLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
        val target = toDate(ts)
        val today = toDate(now)
        return when (target) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> target.format(monthDayFmt)
        }
    }

    /** 回收站用的「还剩 N 天」 */
    fun daysLeft(deadline: Long, now: Long = System.currentTimeMillis()): Int {
        val diff = deadline - now
        if (diff <= 0) return 0
        return ((diff + 24 * 60 * 60 * 1000 - 1) / (24 * 60 * 60 * 1000)).toInt()
    }
}
