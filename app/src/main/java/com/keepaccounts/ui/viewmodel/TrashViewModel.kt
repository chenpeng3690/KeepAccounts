package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.data.local.db.CategoryEntity
import com.keepaccounts.data.local.db.SettingKeys
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.util.Time
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TrashItem(
    val txn: TransactionEntity,
    val deadline: Long,
    val daysLeft: Int
)

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val retentionDays: Int = 7,
    val accounts: Map<Long, AccountEntity> = emptyMap(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val loaded: Boolean = false
)

class TrashViewModel(private val container: AppContainer) : ViewModel() {

    private val retention = container.settingsRepo
        .observe(SettingKeys.RECYCLE_RETENTION_DAYS)
        .map { it?.toIntOrNull() ?: 7 }

    val state: StateFlow<TrashUiState> = combine(
        container.txnRepo.observeTrash(),
        container.categoryRepo.observeAll(),
        container.accountRepo.observeActive(),
        retention
    ) { trash, categories, accounts, days ->
        TrashUiState(
            items = trash.map { txn ->
                val deadline = (txn.deletedAt ?: 0L) + days.toLong() * DAY_MS
                TrashItem(txn = txn, deadline = deadline, daysLeft = Time.daysLeft(deadline))
            },
            retentionDays = days,
            accounts = accounts.associateBy { it.id },
            categories = categories.associateBy { it.id },
            loaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrashUiState()
    )

    suspend fun restore(id: Long) = container.txnRepo.restore(id)

    suspend fun purge(id: Long) = container.txnRepo.purge(id)

    suspend fun purgeAll() {
        state.value.items.forEach { container.txnRepo.purge(it.txn.id) }
    }

    suspend fun setRetention(days: Int) {
        container.settingsRepo.put(SettingKeys.RECYCLE_RETENTION_DAYS, days.toString())
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
