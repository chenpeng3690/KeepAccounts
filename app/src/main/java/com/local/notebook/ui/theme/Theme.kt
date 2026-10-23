package com.local.notebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalLedgerPalette = staticCompositionLocalOf { LightLedgerPalette }

object LedgerTheme {
    val palette: LedgerPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalLedgerPalette.current
}

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun NotiLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkLedgerPalette else LightLedgerPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.brand,
            onPrimary = Color.White,
            primaryContainer = palette.brandSoft,
            onPrimaryContainer = palette.brandSoftText,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.ink2,
            outline = palette.line,
            error = palette.expense,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.brand,
            onPrimary = Color.White,
            primaryContainer = palette.brandSoft,
            onPrimaryContainer = palette.brandSoftText,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.ink2,
            outline = palette.line,
            error = palette.expense,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalLedgerPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LedgerTypography,
            shapes = LedgerShapes,
            content = content
        )
    }
}
