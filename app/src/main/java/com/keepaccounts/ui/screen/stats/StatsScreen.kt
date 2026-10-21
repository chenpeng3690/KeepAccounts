package com.keepaccounts.ui.screen.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.BudgetEntity
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaIconButton
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.KaSheetActions
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.toColor
import com.keepaccounts.ui.viewmodel.CategorySlice
import com.keepaccounts.ui.viewmodel.StatsViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time

/**
 * 统计页。对齐 docs/ui-preview.html 第 03 屏：
 * 左侧月份选择 → 收入/支出/结余三格 → 支出构成环形图 → 近 7 天趋势折线 → 本月预算。
 */
@Composable
fun StatsScreen() {
    val vm = kaViewModel { StatsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showBudget by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // nav：左侧月份选择，右侧筛选按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthSwitcher(
                month = state.month,
                onPrev = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) }
            )
            Spacer(Modifier.weight(1f))
            KaIconButton(Icons.Outlined.FilterList, "筛选") { /* 筛选留待后续 */ }
        }

        Trio(income = state.monthIncome, expense = state.monthExpense)

        SectionLabel(text = "支出构成", trailing = "共 ¥${Money.format(state.monthExpense)}")
        KaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = state.slices,
                    center = state.monthExpense,
                    modifier = Modifier.size(118.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (state.slices.isEmpty()) {
                        Text(
                            text = "本月还没有支出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.slices.take(5).forEach { slice -> SliceLegend(slice) }
                    }
                }
            }
        }

        SectionLabel(text = "近 7 天趋势")
        KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            TrendChart(state.trend, state.maxTrend)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Time.toDate(state.trend.firstOrNull()?.first ?: state.month).toString().substring(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Time.toDate(state.trend.lastOrNull()?.first ?: state.month).toString().substring(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionLabel(text = "本月预算")
        BudgetCard(
            expense = state.monthExpense,
            budget = state.budget,
            monthEnd = Time.endOfMonth(state.month),
            onClick = { showBudget = true }
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showBudget) {
        BudgetSheet(
            current = state.budget,
            onDismiss = { showBudget = false },
            onSave = { amount ->
                vm.setBudget(amount)
                showBudget = false
            },
            onClear = {
                vm.clearBudget()
                showBudget = false
            }
        )
    }
}

@Composable
private fun MonthSwitcher(month: Long, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onPrev)
                .padding(horizontal = 8.dp)
        )
        Text(
            text = Time.formatMonth(month),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onNext)
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun Trio(income: Long, expense: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        TrioCell("收入", income, KaTheme.extra.income, Modifier.weight(1f))
        TrioCell("支出", expense, KaTheme.extra.expense, Modifier.weight(1f))
        TrioCell("结余", income - expense, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun TrioCell(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Money.format(cents).substringBefore("."),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DonutChart(slices: List<CategorySlice>, center: Long, modifier: Modifier = Modifier) {
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val uncatColor = MaterialTheme.colorScheme.outline
    val colors = slices.map { it.category?.color?.toColor() ?: uncatColor }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val side = minOf(size.width, size.height)
            val arcSize = androidx.compose.ui.geometry.Size(side - stroke, side - stroke)
            val topLeft = Offset(inset, inset)

            if (slices.isEmpty()) {
                drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var startAngle = -90f
            slices.forEachIndexed { i, s ->
                val sweep = s.percent * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = colors[i],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Money.format(center).substringBefore("."),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "本月支出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SliceLegend(slice: CategorySlice) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    slice.category?.color?.toColor() ?: MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = slice.category?.name ?: "其他",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(slice.percent * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrendChart(trend: List<Pair<Long, Long>>, max: Long) {
    val brand = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        if (trend.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val points = trend.mapIndexed { i, (_, amt) ->
            val x = w * i / (trend.size - 1)
            val y = h - (amt.toFloat() / max.coerceAtLeast(1) * (h - 8.dp.toPx()))
            Offset(x, y)
        }

        val fill = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(brand.copy(alpha = 0.18f), brand.copy(alpha = 0f))
            )
        )

        val line = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(
            path = line,
            color = brand,
            style = Stroke(width = 2.4f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val last = points.last()
        drawCircle(Color.White, 3.2f.dp.toPx(), last)
        drawCircle(brand, 3.2f.dp.toPx(), last, style = Stroke(2.2f.dp.toPx()))
    }
}

@Composable
private fun BudgetCard(
    expense: Long,
    budget: BudgetEntity?,
    monthEnd: Long,
    onClick: () -> Unit
) {
    KaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
        if (budget == null || budget.amount <= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "还没设预算",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClick) { Text("去设置") }
            }
            return@KaCard
        }

        val over = expense > budget.amount
        val color = if (over) KaTheme.extra.expense else KaTheme.extra.income
        val ratio = (expense.toFloat() / budget.amount).coerceIn(0f, 1f)
        val remain = budget.amount - expense
        val daysLeft = Time.daysLeft(monthEnd, System.currentTimeMillis()).coerceAtLeast(1)
        val dailyAvg = if (remain > 0) remain / daysLeft else 0L

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "已用 ${Money.format(expense).substringBefore(".")} / ${Money.format(budget.amount).substringBefore(".")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (over) "超支 ${Money.format(-remain)}" else "剩 ${Money.format(remain).substringBefore(".")}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (over) "已超出预算" else "日均可用 ¥${Money.format(dailyAvg)}，还有 $daysLeft 天",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BudgetSheet(
    current: BudgetEntity?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onClear: () -> Unit
) {
    var amountText by remember { mutableStateOf(current?.amount?.let { Money.format(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    KaSheet(title = "设置本月预算", onDismiss = onDismiss) {
        KaField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "预算金额",
            prefix = "¥",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        )
        KaSheetActions(
            confirmText = "保存",
            onConfirm = {
                val cents = Money.parse(amountText)
                if (cents == null || cents <= 0) {
                    error = "金额格式不对"
                } else {
                    error = null
                    onSave(cents)
                }
            },
            onDismiss = onDismiss,
            errorText = error
        )
        if (current != null) {
            TextButton(
                onClick = onClear,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("清除预算", color = KaTheme.extra.expense)
            }
        }
    }
}
