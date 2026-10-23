package com.local.notebook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 列表分类色块使用的色调，深色模式下自动切换为高亮版本。 */
enum class LedgerTone { RED, ORANGE, BLUE, PURPLE, GREEN, GRAY }

@Composable
fun LedgerTone.contentColor(): Color {
    val p = LedgerTheme.palette
    return when (this) {
        LedgerTone.RED -> p.catRed
        LedgerTone.ORANGE -> p.catOrange
        LedgerTone.BLUE -> p.catBlue
        LedgerTone.PURPLE -> p.catPurple
        LedgerTone.GREEN -> p.catGreen
        LedgerTone.GRAY -> p.catGray
    }
}

@Composable
fun LedgerTone.containerColor(): Color {
    val p = LedgerTheme.palette
    return contentColor().copy(alpha = if (p.isDark) 0.16f else 0.10f)
}
