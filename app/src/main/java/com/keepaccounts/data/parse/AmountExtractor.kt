package com.keepaccounts.data.parse

/**
 * 金额抽取，返回「分」的 Long。
 *
 * 通知里的数字很杂：订单号、日期、手机号、数量、百分比。只有带货币符号
 * （¥ / ￥ / $ / RMB）或「元 / 块」单位的才算金额。抽取时先用正则把所有
 * 「数字 + 可选单位」扫出来，再按「是否带单位」与「离支付关键词的远近」挑最像的那个。
 */
object AmountExtractor {

    data class Hit(
        val cents: Long,
        val raw: String,
        val start: Int,
        val hasUnit: Boolean
    )

    /** 数字允许千分位（1,286.40），也允许无小数（25）或与「元」粘连（12.50元） */
    private val AMOUNT = Regex(
        """(¥|￥|\$|RMB)?\s*(\d{1,3}(?:,\d{3})+|\d+)(\.\d{1,2})?\s*(元|块|rmb|RMB)?"""
    )

    fun hits(text: String): List<Hit> {
        val out = mutableListOf<Hit>()
        for (m in AMOUNT.findAll(text)) {
            val currency = m.groupValues[1]
            val intPart = m.groupValues[2].replace(",", "")
            val dec = m.groupValues[3]
            val unit = m.groupValues[4]
            val cents = toCents(intPart, dec)
            if (cents <= 0) continue
            out.add(
                Hit(
                    cents = cents,
                    raw = m.value,
                    start = m.range.first,
                    hasUnit = currency.isNotEmpty() || unit.isNotEmpty()
                )
            )
        }
        return out
    }

    /** 从一段文本里抽出「最像付款金额」的那个；抽不出返回 null */
    fun extract(text: String): Long? {
        val all = hits(text)
        if (all.isEmpty()) return null
        // 优先带单位的数字；整段都没有单位（比如标题里一个孤零零的数字）才退而求其次
        val pool = all.filter { it.hasUnit }.ifEmpty { all }

        val anchor = paymentAnchor(text)
        if (anchor >= 0) {
            // 支付关键词之后出现的第一个金额，通常是实际成交额而非原价/优惠额
            val after = pool.filter { it.start >= anchor }
            if (after.isNotEmpty()) return after.first().cents
            return pool.minByOrNull { kotlin.math.abs(it.start - anchor) }?.cents
        }
        return pool.first().cents
    }

    private fun paymentAnchor(text: String): Int {
        for (kw in listOf(
            "支付成功", "付款成功", "已支付", "实付", "实收",
            "收款", "到账", "退款", "金额", "消费", "扣款"
        )) {
            val i = text.indexOf(kw)
            if (i >= 0) return i
        }
        return -1
    }

    private fun toCents(intPart: String, dec: String): Long {
        val whole = intPart.toLongOrNull() ?: return 0L
        val fraction = when {
            dec.isEmpty() -> 0L
            dec.length == 2 -> dec.substring(1).toLongOrNull() ?: 0L
            else -> (dec.substring(1).padEnd(2, '0').take(2)).toLongOrNull() ?: 0L
        }
        return whole * 100 + fraction
    }
}
