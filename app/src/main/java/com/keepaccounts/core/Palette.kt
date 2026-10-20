package com.keepaccounts.core

/**
 * 全项目唯一色值来源。
 *
 * 存成 hex 字符串，因为数据库里也存字符串；UI 渲染时才 `.toColor()`。
 * 改配色只改这一处。
 */
object Palette {

    /** 主色：靛蓝。刻意避开红绿——那两个位置留给支出/收入 */
    const val BRAND = "#3B5BDB"
    const val BRAND_DARK = "#5B77FF"

    /** 支出 */
    const val EXPENSE = "#E03131"
    const val EXPENSE_DARK = "#FF6B6B"

    /** 收入 */
    const val INCOME = "#0CA678"
    const val INCOME_DARK = "#3DD9A0"

    /** 提醒/待确认 */
    const val WARNING = "#C2760A"
    const val WARNING_DARK = "#E8A23D"

    /** 转账、不计收支 */
    const val TRANSFER = "#9AA1AE"

    const val BG_LIGHT = "#F2F3F6"
    const val SURFACE_LIGHT = "#FFFFFF"
    const val INK_LIGHT = "#12141A"
    const val INK_SECONDARY_LIGHT = "#5B6270"
    const val INK_TERTIARY_LIGHT = "#9AA1AE"
    const val LINE_LIGHT = "#E1E4EA"

    const val BG_DARK = "#0E1116"
    const val SURFACE_DARK = "#171B22"
    const val INK_DARK = "#E9ECF2"
    const val INK_SECONDARY_DARK = "#9BA4B4"
    const val INK_TERTIARY_DARK = "#6B7484"
    const val LINE_DARK = "#252B36"

    /**
     * 12 色分类色板。不含靛蓝系，避免跟主色撞车；
     * 顺序按「食用频率」排，肉眼看过去相邻色差别足够大。
     */
    val CATEGORY = listOf(
        "#E2724F", // 餐饮
        "#E0A34A", // 交通
        "#C9B037", // 购物
        "#6FB356", // 居住
        "#4E9BD1", // 娱乐
        "#8A77D8", // 医疗
        "#C96FA8", // 人情
        "#DE6B72", // 学习
        "#9C7B54", // 通讯
        "#7E9AAF", // 宠物
        "#6287D8", // 其他
        "#A0A6B0"  // 未分类
    )
}
