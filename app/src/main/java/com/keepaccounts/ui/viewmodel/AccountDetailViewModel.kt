package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.TransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AccountDetailUiState(
    val account: AccountEntity? = null,
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val recent: List<TransactionEntity> = emptyList(),
    val allAccounts: List<AccountEntity> = emptyList(),
    val loaded: Boolean = false
)

class AccountDetailViewModel(
    private val container: AppContainer,
    private val accountId: Long
) : ViewModel() {

    val state: StateFlow<AccountDetailUiState> = combine(
        container.accountRepo.observeById(accountId),
        container.txnRepo.observeAccountMonthExpense(accountId),
        container.txnRepo.observeAccountMonthIncome(accountId),
        container.txnRepo.observeForAccount(accountId, 20),
        container.accountRepo.observeActive()
    ) { account, expense, income, recent, allAccounts ->
        AccountDetailUiState(
            account = account,
            monthExpense = expense,
            monthIncome = income,
            recent = recent,
            allAccounts = allAccounts,
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountDetailUiState()
    )

    suspend fun transfer(toAccountId: Long, amountCents: Long, note: String?): String? {
        val account = container.accountRepo.findById(accountId) ?: return "账户不存在"
        if (toAccountId == accountId) return "转出与转入不能是同一个账户"
        if (amountCents <= 0) return "金额必须大于 0"
        return runCatching {
            container.accountRepo.transfer(
                fromAccountId = account.id,
                toAccountId = toAccountId,
                amount = amountCents,
                note = note?.takeIf { it.isNotBlank() }
            )
            null
        }.getOrElse { it.message ?: "转账失败" }
    }

    suspend fun adjust(actualBalanceSigned: Long): String? = runCatching {
        container.accountRepo.adjustBalance(accountId, actualBalanceSigned)
        null
    }.getOrElse { it.message ?: "校准失败" }

    suspend fun updateProfile(
        name: String,
        type: String,
        creditLimit: Long?,
        billDay: Int?,
        repayDay: Int?,
        includeInNetWorth: Boolean,
        color: String
    ): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "账户名不能为空"
        val existing = container.accountRepo.findByName(trimmed)
        if (existing != null && existing.id != accountId) return "已有同名账户"
        return runCatching {
            container.accountRepo.updateProfile(
                id = accountId,
                name = trimmed,
                type = type,
                creditLimit = creditLimit,
                billDay = billDay,
                repayDay = repayDay,
                includeInNetWorth = includeInNetWorth,
                color = color
            )
            null
        }.getOrElse { it.message ?: "保存失败" }
    }

    suspend fun deleteAccount(): Int {
        val count = container.accountRepo.transactionCount(accountId)
        if (count > 0) return count
        container.accountRepo.delete(accountId)
        return 0
    }
}
