package com.seebudget.app.domain.repository

import com.seebudget.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

/** RF-02 — acceso a categorías (predefinidas + personalizadas). */
interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getById(id: String): Category?

    /** Siempre crea una categoría personalizada (isDefault = false). */
    suspend fun create(name: String, icon: String, color: String): Category

    suspend fun update(category: Category)
    suspend fun delete(id: String)
}
