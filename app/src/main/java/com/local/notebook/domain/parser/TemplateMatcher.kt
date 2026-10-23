package com.local.notebook.domain.parser

/** 按顺序匹配字面片段，片段之间允许任意无关内容。 */
object TemplateMatcher {

    private val removable = Regex("""[\s\p{Punct}，。！？、：；·—…￥¥“”‘’（）【】]""")

    fun matches(text: String, rule: RuleDefinition): Boolean {
        val haystack = if (rule.fuzzy) normalize(text) else text
        var position = 0
        for (group in rule.literalGroups) {
            val hit = group
                .map { if (rule.fuzzy) normalize(it) else it }
                .filter { it.isNotEmpty() }
                .mapNotNull { literal ->
                    val index = haystack.indexOf(literal, position)
                    if (index >= 0) index + literal.length else null
                }
                .minOrNull()
            if (hit == null) return false
            position = hit
        }
        return rule.literalGroups.isNotEmpty()
    }

    private fun normalize(value: String): String =
        removable.replace(value.lowercase(), "")
}
