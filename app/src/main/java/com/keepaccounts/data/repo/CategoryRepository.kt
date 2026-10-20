package com.keepaccounts.data.repo

import com.keepaccounts.data.local.db.AppDatabase
import com.keepaccounts.data.local.db.CategoryDao
import com.keepaccounts.data.local.db.CategoryEntity
import kotlinx.coroutines.flow.Flow

class CategoryRepository(db: AppDatabase) {

    private val dao: CategoryDao = db.categoryDao()

    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    fun observeByType(type: String): Flow<List<CategoryEntity>> = dao.observeByType(type)

    suspend fun listAll(): List<CategoryEntity> = dao.listAll()

    suspend fun findById(id: Long): CategoryEntity? = dao.findById(id)

    suspend fun count(): Int = dao.count()

    suspend fun create(name: String, type: String, icon: String, color: String): Long {
        val maxSort = dao.listAll().maxOfOrNull { it.sortOrder } ?: 0
        return dao.insert(
            CategoryEntity(
                name = name,
                type = type,
                icon = icon,
                color = color,
                sortOrder = maxSort + 1,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun rename(id: Long, newName: String, newColor: String? = null) {
        val category = dao.findById(id) ?: return
        dao.update(category.copy(name = newName, color = newColor ?: category.color))
    }

    suspend fun setHidden(id: Long, hidden: Boolean) = dao.setHidden(id, hidden)
}
