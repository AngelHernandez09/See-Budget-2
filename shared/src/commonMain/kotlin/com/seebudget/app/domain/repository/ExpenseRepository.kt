package com.seebudget.app.domain.repository

import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.PlannedItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * RF-01 — acceso a gastos. Sigue el patrón offline-first: siempre lee/
 * escribe primero en SQLDelight local (ver ExpenseRepositoryImpl); el
 * SyncManager que reconcilia con Supabase llega en Fase 2.
 *
 * El repositorio genera `id` (UUID) y controla `createdAt`/`updatedAt`/
 * `syncStatus` — quien llama a `create`/`update` no los maneja directo,
 * para mantener esa lógica centralizada en un solo lugar.
 */
interface ExpenseRepository {
    fun observeAll(): Flow<List<Expense>>
    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<Expense>>
    suspend fun getById(id: String): Expense?

    /** Check-in diario (Fase 7, RF-10): gastos/ingresos ya confirmados de un presupuesto para un día puntual. */
    fun observeByBudgetAndDate(budgetId: String, date: LocalDate): Flow<List<Expense>>

    suspend fun create(
        amount: Long,
        currency: String,
        categoryId: String,
        date: LocalDate,
        note: String? = null,
        receiptImageUrl: String? = null,
        paymentMethod: String? = null,
        budgetId: String? = null,
        plannedItemId: String? = null,
        isPlannedOverride: Boolean = false,
        // Fase 7 — ver KDoc de Expense.kt. Default EXPENSE: el flujo
        // genérico de "Registrar gasto" (RF-01) no cambia de significado.
        type: PlannedItemType = PlannedItemType.EXPENSE,
    ): Expense

    /**
     * Actualiza un gasto existente. `id`/`createdAt` de [expense] se
     * preservan tal cual; `updatedAt` y `syncStatus` los recalcula el
     * repositorio.
     */
    suspend fun update(expense: Expense)

    suspend fun delete(id: String)
}
