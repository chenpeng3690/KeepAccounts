package com.local.notebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.notebook.R
import com.local.notebook.ui.theme.LedgerTheme

/** 通知读取被系统关闭时的常驻红色提示条。 */
@Composable
fun NotificationOffBanner(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LedgerTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.dangerSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_alert),
            contentDescription = null,
            tint = p.dangerText,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "通知读取已关闭，记账已停止",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = p.dangerText,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(p.card.copy(alpha = if (p.isDark) 0.10f else 0.7f))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                "去开启",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.dangerText
            )
        }
    }
}
