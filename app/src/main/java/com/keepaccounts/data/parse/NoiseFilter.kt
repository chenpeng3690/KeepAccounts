package com.keepaccounts.data.parse

/**
 * 噪音过滤（L1 / L3）。
 *
 * 逐层否决，任一层命中即拦截，并把「哪一层 + 为什么」交给解析日志。
 * L0（通知 category）在监听器入口做，因为那层不需要落库；
 * L1（全局噪音词）与 L3（结构性否决）在这里做。
 *
 * L1 有一个豁免：同时存在明确支付成功语义词（「支付成功」「到账」等）时，
 * 不因「优惠券 / 红包」这类词误杀一条真实的支付通知。
 */
object NoiseFilter {

    val GLOBAL_NOISE = listOf(
        "优惠券", "优惠", "大促", "满减", "红包", "抽奖", "积分", "会员", "限时", "秒杀",
        "拼团", "直播", "开播", "上新", "推荐", "猜你喜欢", "签到", "邀请", "返现",
        "即将过期", "领取", "预约", "订阅", "版本更新", "升级", "津贴", "省钱卡",
        "百亿补贴", "领券", "下单立减", "首单", "新人", "试用", "关注", "转发", "活动", "立减"
    )

    private val PAYMENT_SEMANTIC = listOf(
        "支付成功", "付款成功", "已支付", "扣款成功", "到账", "收款成功", "退款成功", "已收款", "入账"
    )

    data class Verdict(
        val blocked: Boolean,
        val layer: String? = null,
        val reason: String? = null
    )

    fun filter(text: String): Verdict {
        if (text.isBlank() || text.length < 4) return Verdict(true, "L3", "文本过短")

        if (!text.any { it.isDigit() }) return Verdict(true, "L3", "无数字")

        val hasPayment = PAYMENT_SEMANTIC.any { text.contains(it) }
        if (!hasPayment) {
            GLOBAL_NOISE.firstOrNull { text.contains(it) }?.let {
                return Verdict(true, "L1", "命中噪音词：$it")
            }
        }

        if (!hasAmountForm(text)) return Verdict(true, "L3", "数字不构成金额形态")
        return Verdict(false)
    }

    private fun hasAmountForm(text: String): Boolean =
        Regex("""[¥￥\$]|RMB|\d+\s*(元|块)""").containsMatchIn(text)
}
