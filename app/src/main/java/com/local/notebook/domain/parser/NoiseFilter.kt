package com.local.notebook.domain.parser

/** 多层噪音过滤：L0 通知类型、L1 全局噪音词，L4 的支付成功豁免也在这里。 */
object NoiseFilter {

    val builtinNoiseWords = listOf(
        "活动", "优惠", "大促", "满减", "红包", "优惠券", "券", "抽奖", "积分", "会员",
        "限时", "秒杀", "拼团", "直播", "开播", "上新", "推荐", "猜你喜欢", "签到", "邀请",
        "返现福利", "即将过期", "领取", "预约", "订阅", "版本更新", "升级", "运费券", "津贴",
        "省钱卡", "百亿补贴", "领券", "下单立减", "首单", "新人", "试用", "关注", "转发"
    )

    private val paymentSuccessWords = listOf(
        "支付成功", "付款成功", "已支付", "扣款成功", "到账", "收款成功", "退款成功"
    )

    private val blockedCategories = setOf("promo", "recommendation", "social", "system")

    fun l0Blocked(category: String?): Boolean =
        category?.lowercase() in blockedCategories

    fun l1NoiseWord(text: String): String? {
        if (paymentSuccessWords.any { text.contains(it) }) return null
        val fullReduction = Regex("""满\s*\d+\s*减\s*\d+""").containsMatchIn(text)
        if (fullReduction) return "满减"
        return builtinNoiseWords.firstOrNull { text.contains(it) }
    }
}
