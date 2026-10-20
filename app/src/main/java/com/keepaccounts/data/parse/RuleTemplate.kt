package com.keepaccounts.data.parse

import org.json.JSONArray

/**
 * 片段模板：把「一条通知长什么样」描述成有序片段列表。
 *
 * 两种片段：
 * - [Fragment.Literal]：固定文案，比如「支付成功」，必须在原文中按顺序出现才算命中；
 * - [Fragment.Slot]：可变槽位，决定要抽取哪个字段（amount / merchant / time / direction / account）。
 *
 * 匹配用「有序包含」：所有 literal 依次出现即可，片段之间允许夹任意内容；
 * slot 不参与判真，只标记「这里有个可抽取的字段」。
 *
 * 这个结构只活在引擎内部与数据库里，**永不展示给用户** —— 用户看到的是原文高亮。
 */
sealed class Fragment {
    data class Literal(val text: String) : Fragment()
    data class Slot(val type: SlotType) : Fragment()
}

enum class SlotType { AMOUNT, MERCHANT, TIME, DIRECTION, ACCOUNT }

data class RuleTemplate(val fragments: List<Fragment>) {

    fun matches(text: String): Boolean {
        var cursor = 0
        for (f in fragments) {
            if (f is Fragment.Literal) {
                val idx = text.indexOf(f.text, cursor)
                if (idx < 0) return false
                cursor = idx + f.text.length
            }
        }
        return true
    }

    companion object {
        /** templateJson 形如 [{"t":"literal","v":"支付成功"},{"t":"slot","type":"amount"}] */
        fun fromJson(json: String): RuleTemplate {
            val arr = runCatching { JSONArray(json) }.getOrNull()
                ?: return RuleTemplate(emptyList())
            val list = mutableListOf<Fragment>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                when (o.optString("t")) {
                    "literal" -> o.optString("v").takeIf { it.isNotEmpty() }
                        ?.let { list.add(Fragment.Literal(it)) }
                    "slot" -> when (o.optString("type")) {
                        "amount" -> list.add(Fragment.Slot(SlotType.AMOUNT))
                        "merchant" -> list.add(Fragment.Slot(SlotType.MERCHANT))
                        "time" -> list.add(Fragment.Slot(SlotType.TIME))
                        "direction" -> list.add(Fragment.Slot(SlotType.DIRECTION))
                        "account" -> list.add(Fragment.Slot(SlotType.ACCOUNT))
                    }
                }
            }
            return RuleTemplate(list)
        }
    }
}
