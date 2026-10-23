package com.local.notebook.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.notebook.R
import com.local.notebook.service.NotificationAccess
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.rememberNotificationAccessEnabled

@Composable
fun NotificationGuideScreen(
    onEnter: () -> Unit,
    onSkip: () -> Unit
) {
    val p = LedgerTheme.palette
    val context = LocalContext.current
    val enabled = rememberNotificationAccessEnabled()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.9f))
        Text("通知记账", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = p.ink3, letterSpacing = 3.sp)
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(p.brand, p.brand.copy(alpha = 0.72f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tab_pending),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            if (enabled) "通知使用权已开启" else "让每条支付通知\n自动变成账单",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            lineHeight = 31.sp,
            color = p.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "通知记账需要读取你选中的应用通知，才能识别金额并自动入账。",
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
            color = p.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.card)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            GuidePoint("只读取你选中的应用", "默认只开启微信和支付宝，其他应用不会进入解析", R.drawable.ic_grid)
            GuidePoint("数据只存在这台手机", "不联网、不上传，随时可以本地备份", R.drawable.ic_shield)
            GuidePoint("随时可以关闭", "关闭通知使用权后，应用立即停止记账", R.drawable.ic_tab_settings)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (enabled) p.successSoft else p.warnSoft)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (enabled) p.income else p.warn)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "当前状态",
                fontSize = 11.5.sp,
                color = p.ink2,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (enabled) "已开启" else "未开启",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) p.successText else p.warnText
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.brand)
                .clickable {
                    if (enabled) onEnter() else NotificationAccess.openSettings(context)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (enabled) "进入应用" else "去开启通知使用权",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Text(
            if (enabled) "" else "稍后再说",
            fontSize = 12.sp,
            color = p.ink3,
            modifier = Modifier
                .padding(vertical = 14.dp)
                .clickable(enabled = !enabled) { onSkip() }
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun GuidePoint(title: String, description: String, iconRes: Int) {
    val p = LedgerTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(p.brandSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = p.brandSoftText,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
            Text(
                description,
                fontSize = 10.5.sp,
                color = p.ink3,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
