package com.local.notebook.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.data.local.TxnRow
import com.local.notebook.service.NotificationAccess
import com.local.notebook.ui.components.CategoryTile
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.LedgerProgressBar
import com.local.notebook.ui.components.NotificationOffBanner
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.ScreenHeader
import com.local.notebook.ui.components.SourceTag
import com.local.notebook.ui.screens.bills.BillCategoryOption
import com.local.notebook.ui.screens.bills.BillsUiState
import com.local.notebook.ui.screens.bills.BillsViewModel
import com.local.notebook.ui.screens.bills.BudgetUi
import com.local.notebook.ui.screens.bills.DayGroupUi
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.util.formatMoney
import com.local.notebook.ui.util.rememberNotificationAccessEnabled
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    onAdd: () -> Unit,
    onOpenTxn: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: BillsViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accessEnabled = rememberNotificationAccessEnabled()
    var showSearch by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 88.dp)
        ) {
            ScreenHeader(title = "账单") {
                RoundIconButton(
                    icon = R.drawable.ic_search,
                    contentDescription = "搜索",
                    onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.setKeyword("")
                    }
                )
                RoundIconButton(
                    icon = R.drawable.ic_filter,
                    contentDescription = "筛选",
                    onClick = { showFilter = true }
                )
            }

            if (showSearch) {
                SearchBar(
                    value = state.filter.keyword,
                    onValueChange = viewModel::setKeyword,
                    onClear = { viewModel.setKeyword("") },
                    onClose = {
                        viewModel.setKeyword("")
                        showSearch = false
                    }
                )
            }

            MonthSwitcher(
                monthText = state.monthText,
                isCurrentMonth = state.isCurrentMonth,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onBackToCurrent = viewModel::goToCurrentMonth
            )

            if (!accessEnabled) {
                NotificationOffBanner(
                    modifier = Modifier.padding(bottom = 10.dp),
                    onClick = { NotificationAccess.openSettings(context) }
                )
            }

            if (state.loading) {
                LoadingSummary()
            } else {
                SummaryCard(state)
            }

            if (!state.loading) {
                state.budget?.let { budget ->
                    BudgetCard(budget)
                }
            }

            if (state.hasActiveFilters) {
                ActiveFilters(
                    state = state,
                    onClearKeyword = { viewModel.setKeyword("") },
                    onClearType = { viewModel.setType(null) },
                    onClearCategory = { viewModel.setCategory(null) },
                    onClearAccount = { viewModel.setAccount(null) },
                    onClearSource = { viewModel.setSource(null) },
                    onClearAmount = { viewModel.setAmountRange(null, null) }
                )
            }

            if (!state.loading && state.groups.isEmpty()) {
                when {
                    state.hasActiveFilters -> EmptyResult(
                        icon = R.drawable.ic_filter,
                        title = "没有符合条件的账单",
                        subtitle = "试试调整关键词或筛选条件",
                        actionLabel = "清除筛选",
                        onAction = viewModel::clearFilters
                    )
                    !state.isCurrentMonth -> EmptyResult(
                        icon = R.drawable.ic_tab_bills,
                        title = "该月没有账单",
                        subtitle = "可以切换月份查看其他记录",
                        actionLabel = "回到本月",
                        onAction = viewModel::goToCurrentMonth
                    )
                    else -> EmptyBills(onAdd)
                }
            } else {
                LedgerCard(
                    modifier = Modifier.padding(top = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    state.groups.forEach { group ->
                        DayHeader(group)
                        group.rows.forEachIndexed { index, row ->
                            key(row.id) {
                                if (index > 0) {
                                    HorizontalDivider(thickness = 1.dp, color = p.divider)
                                }
                                SwipeableTxnRow(
                                    row = row,
                                    onOpen = { onOpenTxn(row.id) },
                                    onDelete = { id ->
                                        scope.launch {
                                            viewModel.softDelete(id)
                                            val result = snackbarHostState.showSnackbar(
                                                message = "已移入回收站",
                                                actionLabel = "撤销",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restore(id)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!state.loading) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .size(52.dp),
                shape = CircleShape,
                containerColor = p.brand,
                contentColor = Color.White
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_plus),
                    contentDescription = "记一笔",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (showFilter) {
        FilterSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showFilter = false }
        )
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val p = LedgerTheme.palette
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(13.dp),
        color = p.card
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = null,
                tint = p.ink3,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.5.sp, color = p.ink),
                cursorBrush = SolidColor(p.brand),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text("搜索商户、备注、分类或账户", fontSize = 12.5.sp, color = p.ink3)
                        }
                        inner()
                    }
                }
            )
            if (value.isNotEmpty()) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "清空关键词",
                    tint = p.ink3,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable(onClick = onClear)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                "取消",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = p.brand,
                modifier = Modifier.clickable(onClick = onClose)
            )
        }
    }
}

@Composable
private fun MonthSwitcher(
    monthText: String,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackToCurrent: () -> Unit
) {
    val p = LedgerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_left),
            contentDescription = "上个月",
            tint = p.ink3,
            modifier = Modifier
                .size(15.dp)
                .clickable(onClick = onPrevious)
        )
        Text(
            monthText.ifBlank { "本月" },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.ink,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = "下个月",
            tint = if (isCurrentMonth) p.ink3.copy(alpha = 0.3f) else p.ink3,
            modifier = Modifier
                .size(15.dp)
                .clickable(enabled = !isCurrentMonth, onClick = onNext)
        )
        Spacer(Modifier.weight(1f))
        if (!isCurrentMonth) {
            Text(
                "回到本月",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = p.brand,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.brandSoft)
                    .clickable(onClick = onBackToCurrent)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun LoadingSummary() {
    val p = LedgerTheme.palette
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = p.card,
        shadowElevation = if (p.isDark) 0.dp else 1.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .width(54.dp)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(p.track)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .width(150.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.track)
            )
        }
    }
}

@Composable
private fun SummaryCard(state: BillsUiState) {
    val p = LedgerTheme.palette
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = p.card,
        shadowElevation = if (p.isDark) 0.dp else 1.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.monthLabel.ifBlank { "本月支出" },
                        fontSize = 11.sp,
                        color = p.ink3,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "¥",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.expense,
                            modifier = Modifier.padding(bottom = 3.dp, end = 1.dp)
                        )
                        Text(
                            formatMoney(state.expense),
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                            color = p.expense
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("收入", fontSize = 10.5.sp, color = p.ink3)
                    Text(
                        formatMoney(state.income),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.income
                    )
                    Text("结余", fontSize = 10.5.sp, color = p.ink3)
                    Text(
                        formatMoney(state.balance),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.ink
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(budget: BudgetUi) {
    val p = LedgerTheme.palette
    val percent = ((budget.spent.toFloat() / budget.amount) * 100).roundToInt()
    LedgerCard(
        modifier = Modifier.padding(top = 10.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("本月预算已用 $percent%", fontSize = 11.sp, color = p.ink2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (budget.over) "超 " else "剩 ",
                    fontSize = 11.sp,
                    color = if (budget.over) p.expense else p.ink2
                )
                Text(
                    formatMoney(if (budget.over) budget.spent - budget.amount else budget.remaining),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (budget.over) p.expense else p.ink
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LedgerProgressBar(
            progress = budget.progress,
            color = if (budget.over) p.expense else p.brand
        )
    }
}

@Composable
private fun ActiveFilters(
    state: BillsUiState,
    onClearKeyword: () -> Unit,
    onClearType: () -> Unit,
    onClearCategory: () -> Unit,
    onClearAccount: () -> Unit,
    onClearSource: () -> Unit,
    onClearAmount: () -> Unit
) {
    val p = LedgerTheme.palette
    val filter = state.filter
    val chips = buildList {
        if (filter.keyword.isNotBlank()) add(filter.keyword.trim() to onClearKeyword)
        filter.type?.let { add(typeLabel(it) to onClearType) }
        filter.categoryId?.let { id ->
            state.categories.firstOrNull { it.id == id }?.let { add(it.name to onClearCategory) }
        }
        filter.accountId?.let { id ->
            state.accounts.firstOrNull { it.id == id }?.let { add(it.name to onClearAccount) }
        }
        filter.source?.let { add(sourceLabel(it) to onClearSource) }
        if (filter.minCents != null || filter.maxCents != null) {
            val min = filter.minCents?.let { formatMoney(it) } ?: "0.00"
            val max = filter.maxCents?.let { formatMoney(it) } ?: "不限"
            add("¥$min - $max" to onClearAmount)
        }
    }
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "筛选条件",
                fontSize = 10.5.sp,
                color = p.ink3,
                modifier = Modifier.weight(1f)
            )
            Text(
                "共 ${state.resultCount} 笔",
                fontSize = 10.5.sp,
                color = p.ink3
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(chips) { (label, clear) ->
                RemovableChip(label = label, onRemove = clear)
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val p = LedgerTheme.palette
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = p.brandSoft
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = p.brandSoftText)
            Spacer(Modifier.width(4.dp))
            Text("×", fontSize = 11.sp, color = p.brandSoftText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: BillsUiState,
    viewModel: BillsViewModel,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filter = state.filter
    var minInput by remember(filter.minCents) {
        mutableStateOf(filter.minCents?.let(::centsToInput).orEmpty())
    }
    var maxInput by remember(filter.maxCents) {
        mutableStateOf(filter.maxCents?.let(::centsToInput).orEmpty())
    }
    val months = remember {
        (0..11).map { YearMonth.now().minusMonths(it.toLong()) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "筛选",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "清除全部",
                    fontSize = 11.5.sp,
                    color = p.brand,
                    modifier = Modifier.clickable {
                        viewModel.clearFilters()
                        minInput = ""
                        maxInput = ""
                    }
                )
            }

            FilterGroup(title = "月份") {
                items(months) { month ->
                    ChoiceChip(
                        label = monthOptionLabel(month),
                        selected = month == filter.month,
                        onClick = { viewModel.setMonth(month) }
                    )
                }
            }

            FilterGroup(title = "类型") {
                items(
                    listOf<Pair<String?, String>>(
                        null to "全部",
                        "EXPENSE" to "支出",
                        "INCOME" to "收入",
                        "TRANSFER" to "转账"
                    )
                ) { (value, label) ->
                    ChoiceChip(
                        label = label,
                        selected = filter.type == value,
                        onClick = { viewModel.setType(value) }
                    )
                }
            }

            val categoryOptions: List<BillCategoryOption> = if (filter.type == null) {
                state.categories
            } else {
                state.categories.filter { it.type == filter.type }
            }
            FilterGroup(title = "分类") {
                item {
                    ChoiceChip(
                        label = "全部分类",
                        selected = filter.categoryId == null,
                        onClick = { viewModel.setCategory(null) }
                    )
                }
                items(categoryOptions) { category ->
                    ChoiceChip(
                        label = category.name,
                        selected = filter.categoryId == category.id,
                        onClick = { viewModel.setCategory(category.id) }
                    )
                }
            }

            FilterGroup(title = "账户") {
                item {
                    ChoiceChip(
                        label = "全部账户",
                        selected = filter.accountId == null,
                        onClick = { viewModel.setAccount(null) }
                    )
                }
                items(state.accounts) { account ->
                    ChoiceChip(
                        label = account.name,
                        selected = filter.accountId == account.id,
                        onClick = { viewModel.setAccount(account.id) }
                    )
                }
            }

            FilterGroup(title = "来源") {
                items(
                    listOf<Pair<String?, String>>(
                        null to "全部",
                        "NOTIFICATION" to "通知",
                        "MANUAL" to "手动",
                        "IMPORT" to "导入",
                        "TEMPLATE" to "模板",
                        "ADJUSTMENT" to "调整"
                    )
                ) { (value, label) ->
                    ChoiceChip(
                        label = label,
                        selected = filter.source == value,
                        onClick = { viewModel.setSource(value) }
                    )
                }
            }

            Text(
                "金额区间",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = p.ink3,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minInput,
                    onValueChange = { minInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("最低金额", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = maxInput,
                    onValueChange = { maxInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("最高金额", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        viewModel.clearFilters()
                        minInput = ""
                        maxInput = ""
                    }
                ) {
                    Text("重置", color = p.ink2)
                }
                Surface(
                    onClick = {
                        viewModel.setAmountRange(parseCents(minInput), parseCents(maxInput))
                        onDismiss()
                    },
                    shape = RoundedCornerShape(11.dp),
                    color = p.brand
                ) {
                    Text(
                        "完成",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val p = LedgerTheme.palette
    Text(
        title,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        color = p.ink3,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        content = content
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LedgerTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) p.brand else p.iconButtonBg
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else p.ink2,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DayHeader(group: DayGroupUi) {
    val p = LedgerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(group.label, fontSize = 11.sp, color = p.ink3, fontWeight = FontWeight.Medium)
        if (group.expenseTotal > 0) {
            Text(
                "支出 ¥${formatMoney(group.expenseTotal)}",
                fontSize = 11.sp,
                color = p.ink3,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTxnRow(
    row: TxnRow,
    onOpen: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val p = LedgerTheme.palette
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(row.id)
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.dangerSoft)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_trash),
                        contentDescription = null,
                        tint = p.dangerText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("删除", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.dangerText)
                }
            }
        },
        content = {
            TxnRowContent(row = row, onClick = onOpen)
        }
    )
}

@Composable
private fun TxnRowContent(row: TxnRow, onClick: () -> Unit) {
    val p = LedgerTheme.palette
    val tone = runCatching { LedgerTone.valueOf(row.categoryTone ?: "GRAY") }
        .getOrDefault(LedgerTone.GRAY)
    val expense = row.type == "EXPENSE"
    val transfer = row.type == "TRANSFER"
    val adjustment = row.source == "ADJUSTMENT"
    val amountColor = when {
        adjustment -> p.ink3
        transfer -> p.ink2
        expense -> p.expense
        else -> p.income
    }
    val prefix = when {
        transfer -> ""
        expense -> "-"
        else -> "+"
    }
    val meta = when {
        adjustment -> "不计收支 · ${row.accountName ?: "未知账户"}"
        transfer -> "${row.accountName.orEmpty()} → ${row.toAccountName.orEmpty()}"
        else -> "${row.categoryName ?: "未分类"} · ${row.accountName ?: "未知账户"}"
    }
    val title = row.merchant?.takeIf { it.isNotBlank() }
        ?: row.note?.takeIf { it.isNotBlank() }
        ?: if (transfer) "转账" else if (expense) "支出" else "收入"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.card)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryTile(text = row.categoryIcon ?: "其", tone = tone)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color = p.ink,
                maxLines = 1
            )
            Text(
                text = meta,
                fontSize = 11.sp,
                color = p.ink3,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = prefix + formatMoney(row.amount),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = amountColor
            )
            Spacer(Modifier.height(3.dp))
            SourceTag(
                text = sourceLabel(row.source),
                dotColor = if (row.source == "NOTIFICATION") p.brand else p.ink3.copy(alpha = 0.55f)
            )
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "NOTIFICATION" -> "通知"
    "MANUAL" -> "手动"
    "IMPORT" -> "导入"
    "TEMPLATE" -> "模板"
    "ADJUSTMENT" -> "调整"
    else -> "手动"
}

private fun typeLabel(type: String): String = when (type) {
    "EXPENSE" -> "支出"
    "INCOME" -> "收入"
    "TRANSFER" -> "转账"
    else -> type
}

private fun monthOptionLabel(month: YearMonth): String {
    val now = YearMonth.now()
    return when (month) {
        now -> "本月"
        now.minusMonths(1) -> "上月"
        else -> if (month.year == now.year) {
            "${month.monthValue}月"
        } else {
            "${month.year}年${month.monthValue}月"
        }
    }
}

private fun centsToInput(cents: Long): String =
    BigDecimal.valueOf(cents)
        .movePointLeft(2)
        .stripTrailingZeros()
        .toPlainString()

private fun parseCents(input: String): Long? =
    input.trim()
        .toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.movePointRight(2)
        ?.toLong()
        ?.takeIf { it > 0 }

@Composable
private fun EmptyResult(
    icon: Int,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val p = LedgerTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(p.brandSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = p.brandSoftText,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.ink,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            subtitle,
            fontSize = 11.sp,
            color = p.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp)
        )
        Surface(
            onClick = onAction,
            modifier = Modifier
                .padding(top = 14.dp)
                .height(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = p.brand
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(actionLabel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyBills(onAdd: () -> Unit) {
    EmptyResult(
        icon = R.drawable.ic_tab_bills,
        title = "还没有账单",
        subtitle = "付款后自动记账，也可以现在手动记一笔",
        actionLabel = "记一笔",
        onAction = onAdd
    )
}
