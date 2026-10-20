package com.keepaccounts.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaccounts.data.local.db.TxSource
import com.keepaccounts.data.local.db.TxType
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.ui.component.LetterAvatar
import com.keepaccounts.ui.kaViewModel
import com.keepaccounts.ui.theme.KaTheme
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.ui.viewmodel.SearchRange
import com.keepaccounts.ui.viewmodel.SearchUiState
import com.keepaccounts.ui.viewmodel.SearchViewModel
import com.keepaccounts.util.Money
import com.keepaccounts.util.Time

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenTxn: (Long) -> Unit) {
    val vm = kaViewModel { SearchViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedTextField(
                value = state.keyword,
                onValueChange = vm::setKeyword,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索商户或备注", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }

        FilterChipRow(
            options = listOf("全部", "支出", "收入"),
            selectedIndex = when (state.typeFilter) {
                TxType.EXPENSE -> 1
                TxType.INCOME -> 2
                else -> 0
            },
            onSelect = { index ->
                vm.setType(
                    when (index) {
                        1 -> TxType.EXPENSE
                        2 -> TxType.INCOME
                        else -> ""
                    }
                )
            }
        )

        FilterChipRow(
            options = SearchRange.entries.map { it.label },
            selectedIndex = SearchRange.entries.indexOf(state.range),
            onSelect = { vm.setRange(SearchRange.entries[it]) }
        )

        val accountNames = listOf("全部账户") + state.accounts.values.map { it.name }
        val accountIds = listOf(0L) + state.accounts.values.map { it.id }
        FilterChipRow(
            options = accountNames,
            selectedIndex = accountIds.indexOf(state.accountFilter).coerceAtLeast(0),
            onSelect = { vm.setAccount(accountIds.getOrElse(it) { 0L }) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共 ${state.results.size} 笔",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "合计 ${if (state.total >= 0) "+" else "-"}${Money.format(if (state.total < 0) -state.total else state.total)}",
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                fontWeight = FontWeight.SemiBold,
                color = if (state.total >= 0) KaTheme.extra.income else KaTheme.extra.expense
            )
        }

        if (state.results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有符合条件的账单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.results, key = { it.id }) { txn ->
                    SearchRow(txn, state) { onOpenTxn(txn.id) }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SearchRow(txn: TransactionEntity, state: SearchUiState, onClick: () -> Unit) {
    val category = txn.categoryId?.let { state.categories[it] }
    val account = state.accounts[txn.accountId]
    val isAdjustment = txn.source == TxSource.ADJUSTMENT
    val income = txn.type == TxType.INCOME || txn.type == TxType.REFUND

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(
            text = category?.name ?: if (isAdjustment) "调" else "?",
            colorHex = category?.color ?: "#A0A6B0",
            size = 34.dp
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = txn.merchant ?: category?.name ?: if (isAdjustment) "余额调整" else "未分类",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = listOfNotNull(
                    Time.formatMonthDay(txn.occurredAt),
                    account?.name
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = (if (income) "+" else "-") + Money.format(txn.amount),
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            fontWeight = FontWeight.SemiBold,
            color = when {
                isAdjustment -> MaterialTheme.colorScheme.onSurfaceVariant
                txn.type == TxType.TRANSFER -> KaTheme.extra.transfer
                income -> KaTheme.extra.income
                else -> KaTheme.extra.expense
            }
        )
    }
}
