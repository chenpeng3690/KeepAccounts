package com.local.notebook.domain.parser

/** 根据商户/商品关键词猜测分类；低于阈值时返回 null，交给用户选择。 */
object CategoryGuesser {

    private val expenseKeywords = listOf(
        "餐饮" to listOf("外卖", "餐饮", "咖啡", "奶茶", "饭店", "餐厅", "瑞幸", "星巴克", "麦当劳", "肯德基", "美团", "饿了么"),
        "交通" to listOf("打车", "滴滴", "地铁", "公交", "加油", "高铁", "火车", "机票", "出行", "停车"),
        "通讯" to listOf("话费", "流量", "宽带", "中国移动", "中国联通", "中国电信"),
        "居住" to listOf("电费", "水费", "燃气", "物业", "房租", "租金", "家居"),
        "娱乐" to listOf("电影", "演出", "游戏", "KTV", "剧本杀"),
        "医疗" to listOf("药店", "医院", "挂号", "诊所", "药品"),
        "购物" to listOf("超市", "便利店", "生鲜", "淘宝", "京东", "拼多多", "商城", "购物"),
        "教育" to listOf("学费", "课程", "培训", "书店"),
        "旅行" to listOf("酒店", "民宿", "旅游", "景区", "机票"),
        "数码" to listOf("手机", "电脑", "数码", "耳机"),
        "服饰" to listOf("衣服", "鞋", "服饰", "服装"),
        "美容" to listOf("美容", "理发", "美发"),
        "运动" to listOf("健身", "运动", "球馆"),
        "宠物" to listOf("宠物", "猫粮", "狗粮"),
        "保险" to listOf("保险"),
        "税费" to listOf("税费", "缴税")
    )

    private val incomeKeywords = listOf(
        "退款" to listOf("退款"),
        "工资" to listOf("工资", "薪资"),
        "奖金" to listOf("奖金", "年终奖"),
        "报销" to listOf("报销"),
        "投资收益" to listOf("收益", "利息", "分红"),
        "红包" to listOf("红包")
    )

    fun guess(text: String, direction: Direction): String? {
        val table = when (direction) {
            Direction.INCOME -> incomeKeywords
            Direction.EXPENSE -> expenseKeywords
            Direction.UNKNOWN -> return null
        }
        return table
            .map { (category, keywords) -> category to keywords.count { text.contains(it) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }
}
