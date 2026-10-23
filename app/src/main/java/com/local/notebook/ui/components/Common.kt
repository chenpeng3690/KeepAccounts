package com.local.notebook.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.theme.containerColor
import com.local.notebook.ui.theme.contentColor

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LedgerTheme.palette
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = p.card,
        shadowElevation = if (p.isDark) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val p = LedgerTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            color = p.ink,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val p = LedgerTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier.size(30.dp),
        shape = RoundedCornerShape(10.dp),
        color = p.iconButtonBg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp),
                tint = p.ink2
            )
        }
    }
}

@Composable
fun CategoryTile(
    text: String,
    tone: LedgerTone,
    modifier: Modifier = Modifier,
    size: Int = 36,
    corner: Int = 12,
    fontSize: TextUnit = 13.sp
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(tone.containerColor()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = tone.contentColor()
        )
    }
}

@Composable
fun SourceTag(text: String, dotColor: Color) {
    val p = LedgerTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 9.5.sp, color = p.ink3)
    }
}

enum class BadgeKind { ACTIVE, WATCH, OFF, INFO }

@Composable
fun LedgerBadge(text: String, kind: BadgeKind = BadgeKind.INFO) {
    val p = LedgerTheme.palette
    val bg = when (kind) {
        BadgeKind.ACTIVE -> p.successSoft
        BadgeKind.WATCH -> p.warnSoft
        BadgeKind.OFF -> p.line
        BadgeKind.INFO -> p.brandSoft
    }
    val fg = when (kind) {
        BadgeKind.ACTIVE -> p.successText
        BadgeKind.WATCH -> p.warnText
        BadgeKind.OFF -> if (p.isDark) p.ink3 else Color(0xFF8A91A0)
        BadgeKind.INFO -> p.brandSoftText
    }
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun LedgerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LedgerTheme.palette.brand
) {
    val p = LedgerTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(p.track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
fun LedgerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LedgerTheme.palette
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        label = "switchOffset"
    )
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) p.brand else p.switchOffTrack)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = knobOffset, y = 2.dp)
                .size(18.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(if (p.isDark) p.ink else Color.White)
        )
    }
}

@Composable
fun MiniAction(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit = {}
) {
    val p = LedgerTheme.palette
    val bg = when {
        primary -> p.brand
        danger -> p.dangerSoft
        else -> p.track
    }
    val fg = when {
        primary -> Color.White
        danger -> p.dangerText
        else -> p.ink2
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(9.dp),
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

@Composable
fun ChevronIcon(modifier: Modifier = Modifier, tint: Color = LedgerTheme.palette.ink3) {
    Icon(
        painter = painterResource(id = com.local.notebook.R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = modifier.size(13.dp),
        tint = tint
    )
}

@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    val p = LedgerTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.ink3,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Text(text = trailing, fontSize = 11.sp, color = p.ink3.copy(alpha = 0.7f))
        }
    }
}

