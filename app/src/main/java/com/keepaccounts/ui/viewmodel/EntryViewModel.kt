package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.local.db.TransactionEntity
import com.keepaccounts.data.repo.TxnDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EntryViewModel(
    private val container: AppContainer,
    private val editingId: Long?
) : ViewModel() {

    val isEdit: Boolean = editingId != null

    val accounts = container.accountRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = container.categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editing = MutableStateFlow<TransactionEntity?>(null)
    val editing: StateFlow<TransactionEntity?> = _editing.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                _editing.value = container.txnRepo.findById(editingId)
            }
        }
    }

    suspend fun save(draft: TxnDraft): String? {
        return try {
            if (editingId == null) {
                container.txnRepo.add(draft)
            } else {
                val old = container.txnRepo.findById(editingId)
                    ?: return "账单不存在，可能已被删除"
                container.txnRepo.update(
                    old.copy(
                        type = draft.type,
                        amount = draft.amount,
                        accountId = draft.accountId,
                        toAccountId = draft.toAccountId,
                        categoryId = draft.categoryId,
                        merchant = draft.merchant,
                        note = draft.note,
                        occurredAt = draft.occurredAt
                    )
                )
            }
            null
        } catch (e: Exception) {
            e.message ?: "保存失败"
        }
    }

    suspend fun delete(): String? {
        val id = editingId ?: return null
        return try {
            container.txnRepo.softDelete(id)
            null
        } catch (e: Exception) {
            e.message ?: "删除失败"
        }
    }
}
