package com.keepaccounts.ui.theme

import androidx.compose.ui.graphics.Color
import com.keepaccounts.core.Palette

/** hex 字符串 -> Compose Color。数据库与 Palette 里存的是字符串，渲染时才转换 */
fun String.toColor(): Color = Color(android.graphics.Color.parseColor(this))

object KaColors {
    val Brand = Palette.BRAND.toColor()
    val BrandDark = Palette.BRAND_DARK.toColor()
    val Expense = Palette.EXPENSE.toColor()
    val ExpenseDark = Palette.EXPENSE_DARK.toColor()
    val Income = Palette.INCOME.toColor()
    val IncomeDark = Palette.INCOME_DARK.toColor()
    val Warning = Palette.WARNING.toColor()
    val WarningDark = Palette.WARNING_DARK.toColor()

    val BgLight = Palette.BG_LIGHT.toColor()
    val SurfaceLight = Palette.SURFACE_LIGHT.toColor()
    val InkLight = Palette.INK_LIGHT.toColor()
    val InkSecondaryLight = Palette.INK_SECONDARY_LIGHT.toColor()
    val InkTertiaryLight = Palette.INK_TERTIARY_LIGHT.toColor()
    val LineLight = Palette.LINE_LIGHT.toColor()

    val BgDark = Palette.BG_DARK.toColor()
    val SurfaceDark = Palette.SURFACE_DARK.toColor()
    val InkDark = Palette.INK_DARK.toColor()
    val InkSecondaryDark = Palette.INK_SECONDARY_DARK.toColor()
    val InkTertiaryDark = Palette.INK_TERTIARY_DARK.toColor()
    val LineDark = Palette.LINE_DARK.toColor()
}
