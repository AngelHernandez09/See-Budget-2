package com.seebudget.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.PlannedItemRepository
import com.seebudget.app.domain.util.newId
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class PlannedItemRepositoryImpl(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
) : PlannedItemRepository {
    private val queries = db.plannedItemQueries

    override fun observeByBudget(budgetId: String): Flow<List<PlannedItem>> =
        queries.selectByBudget(budgetId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): PlannedItem? =
        queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun create(
        budgetId: String,
        name: String,
        categoryId: String?,
        type: PlannedItemType,
        amount: Long,
        currency: String,
        frequency: PlannedItemFrequency,
        billingDay: Int?,
        specificDate: LocalDate?,
        dayOfWeek: DayOfWeek?,
        isActive: Boolean,
    ): PlannedItem {
        val userId = authRepository.currentUserId()
            ?: error("No se puede crear un ítem planificado sin sesión iniciada")
        val now = Clock.System.now()
        val item = PlannedItem(
            id = newId(),
            budgetId = budgetId,
            userId = userId,
            name = name,
            categoryId = categoryId,
            type = type,
            amount = amount,
            currency = currency,
            frequency = frequency,
            billingDay = billingDay,
            specificDate = specificDate,
            dayOfWeek = dayOfWeek,
            isActive = isActive,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            deletedAt = null,
        )
        queries.insert(
            id = item.id,
            budgetId = item.budgetId,
            userId = item.userId,
            name = item.name,
            categoryId = item.categoryId,
            type = item.type,
            amount = item.amount,
            currency = item.currency,
            frequency = item.frequency,
            billingDay = item.billingDay?.toLong(),
            specificDate = item.specificDate,
            dayOfWeek = item.dayOfWeek,
            isActive = item.isActive,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            syncStatus = item.syncStatus,
            deletedAt = item.deletedAt,
        )
        return item
    }

    override suspend fun update(plannedItem: PlannedItem) {
        queries.update(
            name = plannedItem.name,
            categoryId = plannedItem.categoryId,
            type = plannedItem.type,
            amount = plannedItem.amount,
            currency = plannedItem.currency,
            frequency = plannedItem.frequency,
            billingDay = plannedItem.billingDay?.toLong(),
            specificDate = plannedItem.specificDate,
            dayOfWeek = plannedItem.dayOfWeek,
            isActive = plannedItem.isActive,
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
            id = plannedItem.id,
        )
    }

    /** Tombstone, no borrado físico — ver comentario en PlannedItem.sq. */
    override suspend fun delete(id: String) {
        queries.softDelete(
            deletedAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            id = id,
        )
    }
}
