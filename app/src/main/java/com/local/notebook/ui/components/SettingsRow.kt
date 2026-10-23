package com.local.notebook.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.notebook.ui.theme.LedgerTheme

@Composable
fun SettingsRow(
    @DrawableRes icon: Int?,
    iconTint: Color,
    iconBg: Color,
    label: String,
    value: String? = null,
    valueColor: Color = LedgerTheme.palette.ink3,
    showChevron: Boolean = true,
    showDot: Boolean = false,
    dotColor: Color = LedgerTheme.palette.income,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    indent: Boolean = false,
    labelMuted: Boolean = false,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val p = LedgerTheme.palette
    Column {
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = p.divider)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null || onCheckedChange != null) {
                    onClick?.invoke()
                }
                .padding(
                    start = if (indent) 51.dp else 14.dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
            }
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (labelMuted) FontWeight.Normal else FontWeight.Medium,
                color = if (labelMuted) p.ink2 else p.ink,
                modifier = Modifier.weight(1f)
            )
            when {
                checked != null -> LedgerSwitch(
                    checked = checked,
                    onCheckedChange = { onCheckedChange?.invoke(it) }
                )
                value != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showDot) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(value, fontSize = 11.5.sp, color = valueColor)
                    if (showChevron) {
                        Spacer(Modifier.width(6.dp))
                        ChevronIcon()
                    }
                }
            }
        }
    }
}
