package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.core.Palette
import com.keepaccounts.data.local.db.CategoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CategoryManageViewModel(private val container: AppContainer) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = container.categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun add(name: String, type: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "分类名不能为空"
        val exists = container.categoryRepo.listAll()
            .any { it.type == type && it.name == trimmed }
        if (exists) return "已有同名分类"

        val color = Palette.CATEGORY[container.categoryRepo.count() % Palette.CATEGORY.size]
        return try {
            container.categoryRepo.create(trimmed, type, icon = "more", color = color)
            null
        } catch (e: Exception) {
            e.message ?: "添加失败"
        }
    }

    suspend fun rename(id: Long, newName: String): String? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return "分类名不能为空"
        return try {
            container.categoryRepo.rename(id, trimmed)
            null
        } catch (e: Exception) {
            e.message ?: "保存失败"
        }
    }

    suspend fun hide(id: Long) {
        container.categoryRepo.setHidden(id, true)
    }
}
