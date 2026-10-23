package com.local.notebook.ui.screens.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.TxnRow
import com.local.notebook.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class AccountDetailTxnUi(
    val row: TxnRow,
    val signedAmount: Long,
    val outgoingTransfer: Boolean,
    val displayCategory: String
)

data class AccountDetailUiState(
    val loading: Boolean = true,
    val account: AccountEntity? = null,
    val allAccounts: List<AccountEntity> = emptyList(),
    val transactions: List<AccountDetailTxnUi> = emptyList(),
    val monthCount: Int = 0,
    val deleted: Boolean = false
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val repository: LedgerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<Long>("id") ?: -1L
    private val _deleted = MutableStateFlow(false)

    val state: StateFlow<AccountDetailUiState> = combine(
        repository.observeAccount(accountId),
        repository.observeAccountTransactions(accountId),
        repository.accounts,
        _deleted
    ) { account, rows, accounts, deleted ->
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthRows = rows.filter { it.occurredAt in start until end }
        AccountDetailUiState(
            loading = false,
            account = account,
            allAccounts = accounts,
            transactions = rows.map { row -> toUi(row) },
            monthCount = monthRows.size,
            deleted = deleted || account == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountDetailUiState()
    )

    private fun toUi(row: TxnRow): AccountDetailTxnUi {
        val incomingTransfer = row.type == "TRANSFER" && row.toAccountId == accountId
        val outgoingTransfer = row.type == "TRANSFER" && row.accountId == accountId
        val signed = when {
            row.type == "INCOME" -> row.amount
            row.type == "EXPENSE" -> -row.amount
            incomingTransfer -> row.amount
            else -> -row.amount
        }
        val category = when {
            row.type == "TRANSFER" -> if (outgoingTransfer) {
                "转账 · 至 ${row.toAccountName ?: "其他账户"}"
            } else {
                "转账 · 来自 ${row.accountName ?: "其他账户"}"
            }
            row.source == "ADJUSTMENT" -> "余额调整 · 不计收支"
            else -> "${row.categoryName ?: "未分类"} · ${relativeDate(row.occurredAt)}"
        }
        return AccountDetailTxnUi(
            row = row,
            signedAmount = signed,
            outgoingTransfer = outgoingTransfer,
            displayCategory = category
        )
    }

    private fun relativeDate(millis: Long): String {
        val date = com.local.notebook.ui.util.millisToLocalDate(millis)
        return com.local.notebook.ui.util.formatShortDate(millis).ifBlank { date.toString() }
    }

    suspend fun transactionCount(): Int = repository.accountTransactionCount(accountId)

    fun calibrate(targetSignedBalance: Long) {
        viewModelScope.launch { repository.calibrateAccount(accountId, targetSignedBalance) }
    }

    fun transfer(fromId: Long, toId: Long, amount: Long, note: String?) {
        viewModelScope.launch {
            repository.addTransfer(fromId, toId, amount, System.currentTimeMillis(), note)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            if (repository.deleteAccount(accountId)) {
                _deleted.value = true
            }
        }
    }

    fun moveTransactionsAndDelete(targetId: Long) {
        viewModelScope.launch {
            repository.moveAccountTransactions(accountId, targetId)
            if (repository.deleteAccount(accountId)) {
                _deleted.value = true
            }
        }
    }

    suspend fun otherAccounts(): List<AccountEntity> =
        repository.accounts.first().filter { it.id != accountId }
}
