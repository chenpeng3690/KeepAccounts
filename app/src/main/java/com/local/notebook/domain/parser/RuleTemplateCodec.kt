package com.local.notebook.domain.parser

/** 内置规则的模板编码：literal:a,b|literal:c|slot:amount|fuzzy:true|direction:INCOME */
object RuleTemplateCodec {

    data class Decoded(
        val literalGroups: List<List<String>>,
        val fuzzy: Boolean,
        val direction: Direction?
    )

    fun decode(code: String): Decoded {
        val groups = mutableListOf<List<String>>()
        var fuzzy = false
        var direction: Direction? = null
        code.split('|').forEach { token ->
            when {
                token.startsWith("literal:") -> {
                    val literals = token.removePrefix("literal:")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (literals.isNotEmpty()) groups.add(literals)
                }
                token.startsWith("fuzzy:") -> fuzzy = token.substringAfter(':').trim() == "true"
                token.startsWith("direction:") -> {
                    direction = when (token.substringAfter(':').trim().uppercase()) {
                        "INCOME" -> Direction.INCOME
                        "EXPENSE" -> Direction.EXPENSE
                        else -> null
                    }
                }
            }
        }
        return Decoded(groups, fuzzy, direction)
    }
}
