package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.CategoryEntity
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
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val loaded: Boolean = false
)

class AccountDetailViewModel(
    private val container: AppContainer,
    private val accountId: Long
) : ViewModel() {

    private data class Figures(val expense: Long, val income: Long)

    private val figures = combine(
        container.txnRepo.observeAccountMonthExpense(accountId),
        container.txnRepo.observeAccountMonthIncome(accountId)
    ) { expense, income -> Figures(expense, income) }

    val state: StateFlow<AccountDetailUiState> = combine(
        container.accountRepo.observeById(accountId),
        figures,
        container.txnRepo.observeForAccount(accountId, 20),
        container.accountRepo.observeActive(),
        container.categoryRepo.observeAll()
    ) { account, figure, recent, allAccounts, categories ->
        AccountDetailUiState(
            account = account,
            monthExpense = figure.expense,
            monthIncome = figure.income,
            recent = recent,
            allAccounts = allAccounts,
            categories = categories.associateBy { it.id },
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountDetailUiState()
    )

    /** 账户之间转账（含信用卡还款：从储蓄卡转入信用卡） */
    suspend fun transferBetween(
        fromAccountId: Long,
        toAccountId: Long,
        amountCents: Long,
        note: String?
    ): String? {
        if (fromAccountId == toAccountId) return "转出与转入不能是同一个账户"
        if (amountCents <= 0) return "金额必须大于 0"
        return runCatching {
            container.accountRepo.transfer(
                fromAccountId = fromAccountId,
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
