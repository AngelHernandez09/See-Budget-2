package com.seebudget.app.domain.repository

import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * RF-09 — CRUD de ítems planificados de un presupuesto. Fase 5/6: solo
 * persistencia; la lógica de simulación (frecuencia, billingDay, ajuste
 * de mes corto) es del `ProjectionEngine`, no de este repositorio.
 */
interface PlannedItemRepository {
    fun observeByBudget(budgetId: String): Flow<List<PlannedItem>>
    suspend fun getById(id: String): PlannedItem?

    suspend fun create(
        budgetId: String,
        name: String,
        // Fase 7 (RF-10) — ver KDoc de PlannedItem.kt. Nullable: compatibilidad con ítems creados antes de Fase 7.
        categoryId: String?,
        type: PlannedItemType,
        amount: Long,
        currency: String,
        frequency: PlannedItemFrequency,
        billingDay: Int?,
        specificDate: LocalDate?,
        dayOfWeek: DayOfWeek?,
        isActive: Boolean = true,
    ): PlannedItem

    suspend fun update(plannedItem: PlannedItem)
    suspend fun delete(id: String)
}
