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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.BudgetEntity
import com.keepaccounts.ui.component.KaCard
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.KaSheetActions
import com.keepaccounts.ui.component.ScreenHeader
import com.keepaccounts.ui.component.SectionLabel
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.toColor
import com.keepaccounts.ui.viewmodel.CategorySlice
import com.keepaccounts.ui.viewmodel.StatsViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time

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
        ScreenHeader(title = "统计") {
            MonthSwitcher(
                month = state.month,
                onPrev = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) }
            )
        }

        // 收支结余
        KaCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                Stat("支出", state.monthExpense, KaTheme.extra.expense, Modifier.weight(1f))
                Stat("收入", state.monthIncome, KaTheme.extra.income, Modifier.weight(1f))
                Stat("结余", state.monthIncome - state.monthExpense,
                    MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }

        // 预算
        BudgetCard(
            expense = state.monthExpense,
            budget = state.budget,
            monthEnd = Time.endOfMonth(state.month),
            onClick = { showBudget = true }
        )

        // 分类占比
        SectionLabel(text = "分类占比", trailing = if (state.slices.isEmpty()) null else "共 ${state.slices.size} 类")
        if (state.slices.isEmpty()) {
            KaCard {
                Text(
                    text = "本月还没有支出。记一笔或导入账单后，这里会画出每个分类的占比。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            KaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = state.slices,
                        modifier = Modifier.size(120.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        state.slices.take(6).forEach { slice ->
                            SliceLegend(slice)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                state.slices.forEach { slice ->
                    SliceRow(slice)
                }
            }
        }

        // 日趋势
        SectionLabel(text = "每日支出")
        KaCard {
            DailyBarChart(state.dailyExpense, state.maxDaily)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1 日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${Time.toDate(state.month).lengthOfMonth()} 日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onPrev)
                .padding(horizontal = 10.dp)
        )
        Text(
            text = Time.formatMonth(month),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onNext)
                .padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun Stat(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "¥" + Money.format(cents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BudgetCard(
    expense: Long,
    budget: BudgetEntity?,
    monthEnd: Long,
    onClick: () -> Unit
) {
    SectionLabel(text = "本月预算")
    KaCard {
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

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "已用 ¥${Money.format(expense)} / ¥${Money.format(budget.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(4.dp))
                    )
                }
            }
            TextButton(onClick = onClick) { Text("改") }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (over) "已超支 ¥${Money.format(-remain)}" else "剩余 ¥${Money.format(remain)}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!over && remain > 0) {
                Text(
                    text = "日均可用 ¥${Money.format(dailyAvg)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DonutChart(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val uncatColor = MaterialTheme.colorScheme.outline
    val colors = slices.map { it.category?.color?.toColor() ?: uncatColor }

    Canvas(modifier = modifier) {
        val stroke = 24.dp.toPx()
        val inset = stroke / 2
        val side = minOf(size.width, size.height)
        val arcSize = Size(side - stroke, side - stroke)
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
        Spacer(Modifier.width(6.dp))
        Text(
            text = slice.category?.name ?: "未分类",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SliceRow(slice: CategorySlice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    slice.category?.color?.toColor() ?: MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = slice.category?.name ?: "未分类",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "¥${Money.format(slice.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${(slice.percent * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun DailyBarChart(daily: List<Pair<Long, Long>>, maxDaily: Long) {
    val barColor = KaTheme.extra.expense
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        daily.forEach { (_, amt) ->
            val h = if (maxDaily > 0 && amt > 0) {
                (amt.toFloat() / maxDaily * 82f).coerceAtLeast(3f)
            } else 0f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(h.dp)
                    .background(barColor.copy(alpha = 0.85f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            )
        }
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
