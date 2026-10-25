package com.local.notebook.domain.parser

/** 一条待解析的通知文本。 */
data class NotificationPayload(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val notificationKey: String,
    val groupKey: String?,
    val category: String?,
    val postedAt: Long,
    val receivedAt: Long,
    val isGroupSummary: Boolean = false
) {
    val fullText: String
        get() = listOf(title.trim(), text.trim())
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
}

enum class Direction { EXPENSE, INCOME, UNKNOWN }

data class ParsedAmount(val cents: Long, val raw: String, val score: Int)
