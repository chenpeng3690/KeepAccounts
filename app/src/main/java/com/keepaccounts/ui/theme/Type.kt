package com.keepaccounts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/**
 * 默认字体族即可。数字排列用 [TabularNumbers]，让金额在列表里右对齐时不会左右抖。
 */
val KaTypography = Typography()

/**
 * 等宽数字样式片段。
 *
 * 只声明 fontFeatureSettings，其余字段留空 —— 这样 `.merge()` 时只补这一项，
 * 不会把字号字重覆盖掉。所以要用：
 * ```
 * Text(text = "¥25.00", style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
 * ```
 * 反过来写成 `MaterialTheme.typography.titleMedium.merge(LocalTextStyle.current...)`
 * 会把正文的字号带进来，金额一下子变小，是这里最容易踩的坑。
 */
val TabularNumbers: TextStyle = TextStyle(fontFeatureSettings = "tnum")
