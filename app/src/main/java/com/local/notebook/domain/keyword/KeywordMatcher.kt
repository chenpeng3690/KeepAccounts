package com.local.notebook.domain.keyword

/** 白名单应用的通知进入 AI 前的关键词初筛。 */
object KeywordMatcher {

    fun match(text: String, keywords: List<String>): String? {
        if (text.isBlank()) return null
        return keywords.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { text.contains(it, ignoreCase = true) }
    }
}
