package com.keepaccounts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 3 的 colorScheme 里没有「支出/收入/提醒」这三个语义位，
 * error 也不该拿来表示支出（花钱不是错误）。所以另开一组扩展色。
 */
@Immutable
data class KaExtraColors(
    val expense: Color,
    val income: Color,
    val warning: Color,
    val transfer: Color,
    val pendingSurface: Color
)

private val LightExtra = KaExtraColors(
    expense = KaColors.Expense,
    income = KaColors.Income,
    warning = KaColors.Warning,
    transfer = KaColors.InkTertiaryLight,
    pendingSurface = Color(0xFFFDF3E3)
)

private val DarkExtra = KaExtraColors(
    expense = KaColors.ExpenseDark,
    income = KaColors.IncomeDark,
    warning = KaColors.WarningDark,
    transfer = KaColors.InkTertiaryDark,
    pendingSurface = Color(0xFF2A2218)
)

private val LocalKaExtraColors = staticCompositionLocalOf { LightExtra }

private val LightScheme = lightColorScheme(
    primary = KaColors.Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF1FE),
    onPrimaryContainer = Color(0xFF1B2B6B),
    secondary = KaColors.Income,
    onSecondary = Color.White,
    background = KaColors.BgLight,
    onBackground = KaColors.InkLight,
    surface = KaColors.SurfaceLight,
    onSurface = KaColors.InkLight,
    surfaceVariant = Color(0xFFF4F5F8),
    onSurfaceVariant = KaColors.InkSecondaryLight,
    outline = KaColors.LineLight,
    outlineVariant = Color(0xFFECEEF2),
    error = KaColors.Expense,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFFC92A2A)
)

private val DarkScheme = darkColorScheme(
    primary = KaColors.BrandDark,
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF1B2340),
    onPrimaryContainer = Color(0xFFC7D0FF),
    secondary = KaColors.IncomeDark,
    onSecondary = Color(0xFF04231A),
    background = KaColors.BgDark,
    onBackground = KaColors.InkDark,
    surface = KaColors.SurfaceDark,
    onSurface = KaColors.InkDark,
    surfaceVariant = Color(0xFF1F242D),
    onSurfaceVariant = KaColors.InkSecondaryDark,
    outline = KaColors.LineDark,
    outlineVariant = Color(0xFF1E242E),
    error = KaColors.ExpenseDark,
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1A1A),
    onErrorContainer = Color(0xFFFF8787)
)

object KaTheme {
    /** 扩展语义色：`KaTheme.extra.expense` */
    val extra: KaExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKaExtraColors.current
}

@Composable
fun KeepAccountsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalKaExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = KaTypography,
            content = content
        )
    }
}
