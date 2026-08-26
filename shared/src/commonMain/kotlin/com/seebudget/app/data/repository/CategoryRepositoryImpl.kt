package com.seebudget.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.util.newId
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
) : CategoryRepository {
    private val queries = db.categoryQueries

    override fun observeAll(): Flow<List<Category>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): Category? =
        queries.selectById(id).executeAsOneOrNull()?.toDomain()

    /**
     * Siempre crea una categoría PERSONAL (isDefault = false, userId del
     * usuario logueado) — las predefinidas (userId null) se siembran solo
     * desde el schema local + la migración de Supabase, nunca desde acá.
     */
    override suspend fun create(name: String, icon: String, color: String): Category {
        val userId = authRepository.currentUserId()
            ?: error("No se puede crear una categoría sin sesión iniciada")
        val now = Clock.System.now()
        val category = Category(
            id = newId(),
            userId = userId,
            name = name,
            icon = icon,
            color = color,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            deletedAt = null,
        )
        queries.insert(
            id = category.id,
            userId = category.userId,
            name = category.name,
            icon = category.icon,
            color = category.color,
            isDefault = category.isDefault,
            createdAt = category.createdAt,
            updatedAt = category.updatedAt,
            syncStatus = category.syncStatus,
            deletedAt = category.deletedAt,
        )
        return category
    }

    override suspend fun update(category: Category) {
        queries.update(
            name = category.name,
            icon = category.icon,
            color = category.color,
            isDefault = category.isDefault,
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
            id = category.id,
        )
    }

    /** Tombstone (deletedAt), no borrado físico — ver comentario en Category.sq. */
    override suspend fun delete(id: String) {
        queries.softDelete(
            deletedAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            id = id,
        )
    }
}
