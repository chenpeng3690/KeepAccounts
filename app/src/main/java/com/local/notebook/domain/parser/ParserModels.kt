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

data class RuleDefinition(
    val id: Long,
    val name: String,
    val packageName: String?,
    val priority: Int,
    val state: String,
    val literalGroups: List<List<String>>,
    val fuzzy: Boolean,
    val explicitDirection: Direction?,
    val excludeKeywords: List<String>,
    val defaultAccountId: Long?,
    val defaultCategoryId: Long?
) {
    val active: Boolean get() = state == "ACTIVE"
}

sealed interface ParseOutcome {
    data class Matched(
        val ruleId: Long,
        val ruleName: String,
        val amount: Long,
        val direction: Direction,
        val merchant: String?,
        val categoryName: String?,
        val defaultAccountId: Long?,
        val defaultCategoryId: Long?
    ) : ParseOutcome

    data class Pending(
        val ruleId: Long?,
        val ruleName: String?,
        val amount: Long,
        val direction: Direction?,
        val merchant: String?,
        val categoryName: String?,
        val defaultAccountId: Long?,
        val defaultCategoryId: Long?,
        val note: String
    ) : ParseOutcome

    data class Rejected(val layer: String, val reason: String) : ParseOutcome

    data object Ignored : ParseOutcome
}
