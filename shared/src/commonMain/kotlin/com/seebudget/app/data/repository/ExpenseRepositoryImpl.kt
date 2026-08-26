package com.seebudget.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.util.newId
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class ExpenseRepositoryImpl(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
) : ExpenseRepository {
    private val queries = db.expenseQueries

    override fun observeAll(): Flow<List<Expense>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<Expense>> =
        queries.selectByDateRange(from, to)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): Expense? =
        queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override fun observeByBudgetAndDate(budgetId: String, date: LocalDate): Flow<List<Expense>> =
        queries.selectByBudgetAndDate(budgetId, date)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(
        amount: Long,
        currency: String,
        categoryId: String,
        date: LocalDate,
        note: String?,
        receiptImageUrl: String?,
        paymentMethod: String?,
        budgetId: String?,
        plannedItemId: String?,
        isPlannedOverride: Boolean,
        type: PlannedItemType,
    ): Expense {
        val userId = authRepository.currentUserId()
            ?: error("No se puede crear un gasto sin sesión iniciada")
        val now = Clock.System.now()
        val expense = Expense(
            id = newId(),
            userId = userId,
            amount = amount,
            currency = currency,
            categoryId = categoryId,
            date = date,
            note = note,
            receiptImageUrl = receiptImageUrl,
            paymentMethod = paymentMethod,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            budgetId = budgetId,
            plannedItemId = plannedItemId,
            isPlannedOverride = isPlannedOverride,
            deletedAt = null,
            type = type,
        )
        queries.insert(
            id = expense.id,
            userId = expense.userId,
            amount = expense.amount,
            currency = expense.currency,
            categoryId = expense.categoryId,
            date = expense.date,
            note = expense.note,
            receiptImageUrl = expense.receiptImageUrl,
            paymentMethod = expense.paymentMethod,
            createdAt = expense.createdAt,
            updatedAt = expense.updatedAt,
            syncStatus = expense.syncStatus,
            budgetId = expense.budgetId,
            plannedItemId = expense.plannedItemId,
            isPlannedOverride = expense.isPlannedOverride,
            deletedAt = expense.deletedAt,
            type = expense.type,
        )
        return expense
    }

    override suspend fun update(expense: Expense) {
        val updated = expense.copy(
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
        )
        queries.update(
            amount = updated.amount,
            currency = updated.currency,
            categoryId = updated.categoryId,
            date = updated.date,
            note = updated.note,
            receiptImageUrl = updated.receiptImageUrl,
            paymentMethod = updated.paymentMethod,
            updatedAt = updated.updatedAt,
            syncStatus = updated.syncStatus,
            budgetId = updated.budgetId,
            plannedItemId = updated.plannedItemId,
            isPlannedOverride = updated.isPlannedOverride,
            type = updated.type,
            id = updated.id,
        )
    }

    /** Tombstone (deletedAt), no borrado físico — ver comentario en Expense.sq. */
    override suspend fun delete(id: String) {
        queries.softDelete(
            deletedAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            id = id,
        )
    }
}
