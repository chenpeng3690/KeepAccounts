package com.local.notebook.ui.screens.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.TxnRow
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.ui.theme.LedgerTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

data class AssetAccountUi(
    val id: Long,
    val name: String,
    val meta: String,
    val balance: Long,
    val liability: Boolean,
    val char: String,
    val tone: LedgerTone
)

data class AssetsUiState(
    val loading: Boolean = true,
    val netWorth: Long = 0,
    val totalAssets: Long = 0,
    val totalLiabilities: Long = 0,
    val monthChange: Long = 0,
    val assets: List<AssetAccountUi> = emptyList(),
    val liabilities: List<AssetAccountUi> = emptyList(),
    val composition: List<Float> = emptyList()
)

@HiltViewModel
class AssetsViewModel @Inject constructor(
    repository: LedgerRepository
) : ViewModel() {

    val state: StateFlow<AssetsUiState> = combine(
        repository.accounts,
        repository.activeTxns
    ) { accounts, txns ->
        buildState(accounts, txns)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsUiState()
    )

    private fun buildState(accounts: List<AccountEntity>, txns: List<TxnRow>): AssetsUiState {
        val netWorth = accounts.filter { it.includeInNetWorth }.sumOf { it.currentBalance }
        val totalAssets = accounts.filter { it.currentBalance > 0 }.sumOf { it.currentBalance }
        val totalLiabilities = accounts.filter { it.currentBalance < 0 }.sumOf { abs(it.currentBalance) }

        val month = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthFlow = txns
            .filter {
                it.occurredAt in start until end &&
                    (it.type == "EXPENSE" || it.type == "INCOME") &&
                    it.source != "ADJUSTMENT"
            }
            .sumOf { if (it.type == "INCOME") it.amount else -it.amount }

        val rows = accounts.map { account ->
            val liability = account.type in LIABILITY_TYPES
            AssetAccountUi(
                id = account.id,
                name = account.name,
                meta = metaFor(account, liability),
                balance = account.currentBalance,
                liability = liability,
                char = account.icon.ifBlank { account.name.take(1) },
                tone = toneFor(account.type)
            )
        }
        val assets = rows.filterNot { it.liability }.sortedByDescending { it.balance }
        val liabilities = rows.filter { it.liability }.sortedBy { it.balance }

        val positive = assets.filter { it.balance > 0 }
        val composition = if (positive.isEmpty()) {
            emptyList()
        } else {
            val total = positive.sumOf { it.balance }.toFloat().coerceAtLeast(1f)
            val top = positive.take(3).map { it.balance / total }
            val other = positive.drop(3).sumOf { it.balance } / total
            (top + other).map { it.coerceAtLeast(0f) }
        }

        return AssetsUiState(
            loading = false,
            netWorth = netWorth,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            monthChange = monthFlow,
            assets = assets,
            liabilities = liabilities,
            composition = composition
        )
    }

    private fun metaFor(account: AccountEntity, liability: Boolean): String {
        if (liability) {
            val limit = account.creditLimit
            val used = abs(account.currentBalance)
            val repay = account.repayDay
            return buildString {
                if (limit != null) {
                    append("可用额度 ")
                    append(formatCompact((limit - used).coerceAtLeast(0)))
                    if (repay != null) append(" · ")
                }
                if (repay != null) append("${repay} 日还款")
                if (limit == null && repay == null) append("欠款账户")
            }
        }
        return when (account.type) {
            "CASH" -> "现金账户"
            "DEBIT" -> "储蓄卡"
            "INVESTMENT" -> "投资账户"
            else -> when {
                account.name.contains("支付宝") -> "余额 + 余额宝"
                account.name.contains("微信") -> "默认账户"
                else -> "账户"
            }
        }
    }

    private fun formatCompact(cents: Long): String =
        com.local.notebook.ui.util.formatMoneyShort(cents)

    private fun toneFor(type: String): LedgerTone = when (type) {
        "CASH" -> LedgerTone.GREEN
        "DEBIT" -> LedgerTone.BLUE
        "CREDIT" -> LedgerTone.RED
        "BNPL" -> LedgerTone.ORANGE
        "LOAN" -> LedgerTone.PURPLE
        "INVESTMENT" -> LedgerTone.GREEN
        else -> LedgerTone.GRAY
    }

    private companion object {
        val LIABILITY_TYPES = setOf("CREDIT", "BNPL", "LOAN")
    }
}
