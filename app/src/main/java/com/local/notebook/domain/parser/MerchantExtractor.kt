package com.local.notebook.domain.parser

/** 从通知文本中提取商户/对方名称，提取不到时返回 null，不生成猜测值。 */
object MerchantExtractor {

    private val removableWords = listOf(
        "微信支付凭证", "微信支付", "微信收款", "收款到账", "退款到账",
        "你已成功付款", "你已成功支付", "付款成功", "支付成功", "收款成功",
        "退款成功", "还款成功", "余额宝收益", "支付宝", "已支付", "已付款",
        "微信", "支付", "付款", "收款", "到账", "退款", "转账", "还款",
        "通知", "凭证", "提醒", "详情", "您", "你已", "你的", "您的"
    )

    private val fillerWords = listOf(
        "订单", "骑手", "已提交", "正在", "点击", "查看", "毫不知情", "敬请"
    )

    private val noiseWords = listOf("优惠", "红包", "活动", "领取", "券", "积分", "会员", "限时")

    fun extract(text: String): String? =
        candidates(text).maxByOrNull { it.length }?.take(30)

    fun candidates(text: String): List<String> {
        var cleaned = text
        removableWords.sortedByDescending { it.length }.forEach { word ->
            cleaned = cleaned.replace(word, " ")
        }
        cleaned = Regex("""[¥￥]\s*[0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?""").replace(cleaned, " ")
        cleaned = Regex("""[0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?\s*(?:元|块)""").replace(cleaned, " ")

        return cleaned
            .split(Regex("[\n，,。；;！!？?：:]"))
            .asSequence()
            .map { it.trim().trim('。', '，', ',', '.', '-', '—', '|', '·', ' ', '“', '”') }
            .filter { it.length in 2..40 }
            .filterNot { candidate ->
                candidate.all { it.isDigit() } ||
                    fillerWords.any { candidate.contains(it) } ||
                    noiseWords.any { candidate.contains(it) }
            }
            .distinct()
            .take(6)
            .toList()
    }
}
