package com.keepaccounts.data.parse

import com.keepaccounts.data.local.db.TxType

/**
 * 商户名抽取 + 分类猜测。
 *
 * 商户名优先取「收款方 / 商户 / 对方」这类引导词之后的文字；取不到再用通知标题
 * （但要排除「微信」「支付宝」这类 App 自身的标题）。分类用关键词表打分，取最高分，
 * 低于阈值就留空让用户在待确认箱里选。
 */
object MerchantClassifier {

    private val GENERIC_TITLES = setOf(
        "微信", "微信支付", "支付宝", "通知", "支付", "微信收款", "支付宝收款", "收款助手"
    )

    private val MERCHANT_LEADS = listOf("收款方", "商户名称", "商户", "对方", "付款给", "向")

    fun extract(title: String?, text: String): String? {
        for (kw in MERCHANT_LEADS) {
            val i = text.indexOf(kw)
            if (i >= 0) {
                val cleaned = cleanMerchant(text.substring(i + kw.length))
                if (cleaned.length >= 2) return cleaned
            }
        }
        val t = title?.trim()
        if (!t.isNullOrBlank() && t !in GENERIC_TITLES && t.length in 2..20) return t
        return null
    }

    private fun cleanMerchant(s: String): String {
        var r = s.trimStart('：', ':', ' ', '，', ',', '。')
        val cut = Regex("""[¥￥\d元块，,。:：；;]""").find(r)?.range?.first ?: r.length
        r = r.substring(0, cut).trim()
        for (w in listOf("成功", "已", "向你", "向您", "的", "订单", "通知", "给你", "给你转了")) {
            if (r.endsWith(w) && r.length > w.length) r = r.dropLast(w.length).trim()
        }
        return r.take(20)
    }

    /** 返回分类「名称」，需要在调用方映射成 id；猜不出返回 null */
    fun guessCategory(text: String, direction: String?): String? {
        if (direction == null) return null
        val table = if (direction == TxType.INCOME) INCOME_MAP else EXPENSE_MAP
        var best: String? = null
        var bestScore = 0
        for ((name, keywords) in table) {
            val score = keywords.count { text.contains(it) }
            if (score > bestScore) {
                bestScore = score
                best = name
            }
        }
        return if (bestScore > 0) best else null
    }

    private val EXPENSE_MAP = listOf(
        "餐饮" to listOf("外卖", "餐饮", "咖啡", "奶茶", "餐厅", "肯德基", "麦当劳", "瑞幸", "星巴克", "美团", "饿了么", "饭"),
        "交通" to listOf("打车", "滴滴", "地铁", "公交", "加油", "高铁", "火车", "飞机", "停车", "出租车", "出行"),
        "购物" to listOf("超市", "便利店", "生鲜", "商场", "淘宝", "京东", "拼多多", "网购", "商城", "天猫"),
        "通讯" to listOf("话费", "流量", "宽带", "移动", "联通", "电信", "充值"),
        "居住" to listOf("电费", "水费", "燃气", "物业", "房租", "供暖"),
        "娱乐" to listOf("电影", "演出", "游戏", "视频", "音乐", "会员", "KTV", "网吧"),
        "医疗" to listOf("药", "医院", "挂号", "诊所", "体检", "医保"),
        "教育" to listOf("学费", "培训", "课程", "书", "教育"),
        "数码" to listOf("手机", "电脑", "数码", "耳机", "配件"),
        "服饰" to listOf("服装", "服饰", "鞋", "衣"),
        "美容" to listOf("美容", "护肤", "化妆", "理发", "美发"),
        "运动" to listOf("健身", "运动", "瑜伽", "游泳"),
        "旅行" to listOf("酒店", "机票", "旅行", "旅游", "民宿"),
        "宠物" to listOf("宠物", "猫粮", "狗粮", "兽医")
    )

    private val INCOME_MAP = listOf(
        "投资收益" to listOf("收益", "理财", "余额宝", "基金", "利息", "分红"),
        "红包" to listOf("红包"),
        "退款" to listOf("退款"),
        "报销" to listOf("报销"),
        "工资" to listOf("工资", "薪资", "薪水")
    )
}
