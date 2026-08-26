package com.seebudget.app.domain.repository

import com.seebudget.app.domain.model.ProjectionSnapshot
import com.seebudget.app.domain.model.ProjectionSnapshotItem
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * RF-09 — Persistencia de las líneas de referencia (`ProjectionSnapshot`)
 * del `ProjectionEngine`. Además de altas/lectura/corrección, expone
 * [delete] para borrar una línea puntual desde "Historial de
 * proyecciones" (agregado en el chat, con confirmación en la UI — ver
 * `ProjectionHistoryScreen`). El otro borrado que existe es la cascada al
 * eliminar el `Budget` dueño (ver `BudgetRepositoryImpl.delete()`, que
 * opera directo contra `db.projectionSnapshotQueries`, mismo patrón que
 * ya usa para `PlannedItem`).
 */
interface ProjectionSnapshotRepository {
    fun observeByBudget(budgetId: String): Flow<List<ProjectionSnapshot>>

    /** La línea de referencia vigente del Dashboard: la más reciente. */
    suspend fun getLatest(budgetId: String): ProjectionSnapshot?
    suspend fun getById(id: String): ProjectionSnapshot?

    /** "Cambio real → generar nueva línea" o el snapshot inicial automático. */
    suspend fun create(
        budgetId: String,
        label: String,
        frozenInitialBalance: Long,
        frozenStartDate: LocalDate,
        frozenBaseCurrency: String,
        frozenPlannedItems: List<ProjectionSnapshotItem>,
    ): ProjectionSnapshot

    /** "Corrección de error": pisa este mismo snapshot, no genera uno nuevo. */
    suspend fun update(snapshot: ProjectionSnapshot)

    /** Borrado individual de una línea de referencia (tombstone, ver ProjectionSnapshot.sq). */
    suspend fun delete(id: String)
}
