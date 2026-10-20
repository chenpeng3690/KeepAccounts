package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.core.Palette
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.AccountType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AssetsUiState(
    val assetAccounts: List<AccountEntity> = emptyList(),
    val liabilityAccounts: List<AccountEntity> = emptyList(),
    val netWorth: Long = 0,
    val totalAssets: Long = 0,
    val totalLiabilities: Long = 0,
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val loaded: Boolean = false
)

sealed interface CreateAccountResult {
    data object Success : CreateAccountResult
    data object NameExists : CreateAccountResult
    data object Failed : CreateAccountResult
}

class AssetsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<AssetsUiState> = combine(
        container.accountRepo.observeActive(),
        container.txnRepo.observeMonthExpense(),
        container.txnRepo.observeMonthIncome()
    ) { accounts, expense, income ->
        val included = accounts.filter { it.includeInNetWorth }
        val totalAssets = included.sumOf { if (it.currentBalance > 0) it.currentBalance else 0L }
        val totalLiabilities = included.sumOf { if (it.currentBalance < 0) -it.currentBalance else 0L }

        AssetsUiState(
            // 分组按账户类型而不是余额符号：余额为 0 的信用卡仍属于负债组，
            // 否则刚建好还没消费的卡会跑到资产组里
            assetAccounts = accounts.filter { it.type !in AccountType.LIABILITY },
            liabilityAccounts = accounts.filter { it.type in AccountType.LIABILITY },
            netWorth = totalAssets - totalLiabilities,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            monthExpense = expense,
            monthIncome = income,
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsUiState()
    )

    suspend fun createAccount(
        name: String,
        type: String,
        balanceSigned: Long,
        creditLimit: Long?,
        billDay: Int?,
        repayDay: Int?,
        includeInNetWorth: Boolean
    ): CreateAccountResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CreateAccountResult.Failed
        if (container.accountRepo.nameExists(trimmed)) return CreateAccountResult.NameExists

        // 颜色按已有账户数轮转，保证新账户和旧的不会撞色
        val index = container.accountRepo.countActive() % Palette.CATEGORY.size
        return runCatching {
            container.accountRepo.create(
                name = trimmed,
                type = type,
                balance = balanceSigned,
                creditLimit = creditLimit,
                billDay = billDay,
                repayDay = repayDay,
                includeInNetWorth = includeInNetWorth,
                color = Palette.CATEGORY[index]
            )
            CreateAccountResult.Success
        }.getOrElse {
            if (container.accountRepo.nameExists(trimmed)) CreateAccountResult.NameExists
            else CreateAccountResult.Failed
        }
    }

    /** 返回被阻止的原因（该账户下的流水条数）；0 表示已删除 */
    suspend fun deleteAccount(id: Long): Int {
        val count = container.accountRepo.transactionCount(id)
        if (count > 0) return count
        container.accountRepo.delete(id)
        return 0
    }
}
