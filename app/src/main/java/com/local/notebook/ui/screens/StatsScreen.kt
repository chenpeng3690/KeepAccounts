package com.local.notebook.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.LedgerProgressBar
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.SectionHeader
import com.local.notebook.ui.screens.stats.CategorySliceUi
import com.local.notebook.ui.screens.stats.DayPointUi
import com.local.notebook.ui.screens.stats.StatsViewModel
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.formatMoney
import com.local.notebook.ui.util.formatMoneyShort
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expenseComposition = state.composition == "EXPENSE"
    val compositionTotal = if (expenseComposition) state.expense else state.income
    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_left),
                contentDescription = "上个月",
                tint = p.ink3,
                modifier = Modifier
                    .size(15.dp)
                    .clickable(onClick = viewModel::previousMonth)
            )
            Text(
                state.monthLabel.ifBlank { "本月" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = "下个月",
                tint = p.ink3,
                modifier = Modifier
                    .size(15.dp)
                    .clickable(onClick = viewModel::nextMonth)
            )
            Spacer(Modifier.weight(1f))
            RoundIconButton(
                icon = R.drawable.ic_swap,
                contentDescription = "切换收支构成",
                onClick = viewModel::toggleComposition
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrioItem("收入", formatMoneyShort(state.income), p.income, Modifier.weight(1f))
            TrioItem("支出", formatMoneyShort(state.expense), p.expense, Modifier.weight(1f))
            TrioItem("结余", formatMoneyShort(state.balance), p.ink, Modifier.weight(1f))
        }

        SectionHeader(
            title = if (expenseComposition) "支出构成" else "收入构成",
            trailing = "共 ${formatMoney(compositionTotal)}",
            modifier = Modifier.padding(top = 16.dp)
        )
        LedgerCard(
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(state.categories, compositionTotal, expenseComposition)
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (state.categories.isEmpty()) {
                        Text(
                            if (expenseComposition) "本月还没有支出" else "本月还没有收入",
                            fontSize = 11.sp,
                            color = p.ink3
                        )
                    } else {
                        state.categories.forEachIndexed { index, slice ->
                            LegendItem(
                                color = p.chartColors[index.coerceAtMost(p.chartColors.size - 1)],
                                name = slice.name,
                                percent = "${slice.percent}%"
                            )
                        }
                    }
                }
            }
        }

        SectionHeader(title = "近 7 天趋势")
        LedgerCard(
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 8.dp)
        ) {
            TrendChart(points = state.trend, color = p.brand)
            val labels = trendLabels(state.trend)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach {
                    Text(it, fontSize = 9.5.sp, color = p.ink3)
                }
            }
        }

        SectionHeader(title = "本月预算")
        val budget = state.budget
        if (budget == null) {
            LedgerCard(
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("还没有设置预算", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.ink)
                        Text("设置后首页与统计页会一起显示进度", fontSize = 10.5.sp, color = p.ink3)
                    }
                    Text(
                        "设置",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.brand,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(p.brandSoft)
                            .clickable {
                                budgetInput = ""
                                showBudgetDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            LedgerCard(
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(13.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Text("已用 ", fontSize = 11.5.sp, color = p.ink2)
                        Text(
                            formatMoneyShort(budget.spent),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = p.ink
                        )
                        Text(" / ${formatMoneyShort(budget.amount)}", fontSize = 11.5.sp, color = p.ink2)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (budget.over) "超 ${formatMoneyShort(budget.spent - budget.amount)}"
                            else "剩 ${formatMoneyShort(budget.remaining)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (budget.over) p.expense else p.income
                        )
                        Text(
                            "调整",
                            fontSize = 10.5.sp,
                            color = p.brand,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .clickable {
                                    budgetInput = BigDecimal.valueOf(budget.amount)
                                        .movePointLeft(2)
                                        .stripTrailingZeros()
                                        .toPlainString()
                                    showBudgetDialog = true
                                }
                        )
                    }
                }
                LedgerProgressBar(
                    progress = budget.progress,
                    color = if (budget.over) p.expense else p.brand
                )
                Text(
                    if (budget.over) {
                        "已超出预算，建议调整本月支出"
                    } else {
                        "日均可用 ${formatMoneyShort(budget.dailyAvailable)} 元，还有 ${budget.daysLeft} 天"
                    },
                    fontSize = 11.sp,
                    color = if (budget.over) p.expense else p.ink3,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showBudgetDialog) {
        BudgetDialog(
            value = budgetInput,
            onChange = { budgetInput = it },
            onDismiss = { showBudgetDialog = false },
            onSave = {
                val cents = budgetInput.trim()
                    .toBigDecimalOrNull()
                    ?.setScale(2, RoundingMode.HALF_UP)
                    ?.movePointRight(2)
                    ?.toLong()
                    ?.takeIf { it > 0 }
                if (cents != null) {
                    viewModel.setBudget(cents)
                    showBudgetDialog = false
                }
            }
        )
    }
}

@Composable
private fun BudgetDialog(
    value: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("本月总预算", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column {
                Text(
                    "用于首页与统计页的月度支出进度。",
                    fontSize = 11.5.sp,
                    color = p.ink2
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    placeholder = { Text("例如 6000", fontSize = 12.sp) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("保存", color = p.brand) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = p.ink2) }
        }
    )
}

@Composable
private fun TrioItem(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val p = LedgerTheme.palette
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = p.card,
        shadowElevation = if (p.isDark) 0.dp else 1.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(label, fontSize = 10.sp, color = p.ink3)
            Text(
                value,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = valueColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DonutChart(categories: List<CategorySliceUi>, total: Long, expense: Boolean) {
    val p = LedgerTheme.palette
    Box(modifier = Modifier.size(118.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 23.dp.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (total <= 0 || categories.isEmpty()) {
                drawArc(
                    color = p.track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
            } else {
                var start = -90f
                categories.forEachIndexed { index, slice ->
                    val sweep = 360f * slice.amount / total
                    drawArc(
                        color = p.chartColors[index.coerceAtMost(p.chartColors.size - 1)],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatMoneyShort(total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text(if (expense) "本月支出" else "本月收入", fontSize = 9.5.sp, color = p.ink3)
        }
    }
}

@Composable
private fun LegendItem(color: Color, name: String, percent: String) {
    val p = LedgerTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            name,
            fontSize = 11.5.sp,
            color = p.ink2,
            modifier = Modifier
                .padding(start = 7.dp)
                .weight(1f)
        )
        Text(percent, fontSize = 10.5.sp, color = p.ink3)
    }
}

@Composable
private fun TrendChart(points: List<DayPointUi>, color: Color) {
    val p = LedgerTheme.palette
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        if (points.isEmpty()) return@Canvas
        val values = points.map { it.amount }
        val maxValue = values.maxOrNull() ?: 0L
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        val offsets = values.mapIndexed { index, value ->
            val ratio = if (maxValue <= 0) 0f else value.toFloat() / maxValue
            Offset(
                x = index * stepX,
                y = size.height - 6f - ratio * (size.height - 14f)
            )
        }
        val line = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.18f), Color.Transparent),
                startY = 0f,
                endY = size.height
            )
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        val highlight = offsets[values.indexOf(maxValue).coerceAtLeast(0)]
        drawCircle(color = p.card, radius = 3.2.dp.toPx(), center = highlight)
        drawCircle(
            color = color,
            radius = 3.2.dp.toPx(),
            center = highlight,
            style = Stroke(width = 2.2.dp.toPx())
        )
    }
}

private fun trendLabels(points: List<DayPointUi>): List<String> {
    if (points.isEmpty()) return emptyList()
    val formatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
    val indices = listOf(0, 2, 4, 6).filter { it in points.indices }
    return indices.map { points[it].date.format(formatter) }
}
