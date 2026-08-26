package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.ProjectionSnapshot
import com.seebudget.app.domain.projection.ProjectionEngine
import com.seebudget.app.domain.projection.ProjectionResult
import com.seebudget.app.domain.projection.ProjectionSnapshotComparer
import com.seebudget.app.domain.projection.ProjectionSnapshotDiff
import com.seebudget.app.domain.projection.ProjectionTemplateItem
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.ProjectionSnapshotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * RF-09 — "Historial de proyecciones" (documento, pantalla 8): lista de
 * `ProjectionSnapshot` de un presupuesto, cada uno con su propia
 * mini-proyección (para el sparkline de preview) y el diff contra el
 * anterior cronológicamente (ver `ProjectionSnapshotComparer`).
 *
 * Cada mini-proyección se calcula con `today = null` (100% plantilla
 * congelada, igual que la línea de referencia del Dashboard — ver KDoc
 * de `BudgetDashboardViewModel`) y usa el `endDate` VIGENTE del
 * presupuesto para acotar el rango, mismo criterio que el Dashboard (el
 * snapshot no congela `endDate`, ver KDoc de `ProjectionSnapshot`).
 *
 * Orden de la lista: más reciente primero (mismo criterio que
 * "Presupuestos — Lista"). El diff de cada entrada es contra el snapshot
 * cronológicamente anterior (no necesariamente el que está debajo en la
 * lista visualmente, aunque en la práctica coincide) — `null` para el
 * snapshot más viejo, no hay nada contra qué compararlo.
 *
 * Vista de "gráfico combinado" (documento: lo marca como "opcional") —
 * no implementada en este paso; queda como mejora si hace falta más
 * adelante.
 *
 * La línea "original" (la primera cronológicamente — la que se
 * autogenera al crear el presupuesto, ver `ensureInitialSnapshot` en
 * `BudgetDashboardViewModel`) NO se puede borrar desde acá: solo las
 * líneas adicionales que el usuario genera después (vía el diálogo
 * "cambio real" o el botón manual "Crear nueva línea de referencia
 * desde hoy") — regla confirmada con el usuario en el chat. Se marca con
 * `HistoryEntry.isOriginal` (el primer elemento de la lista ordenada por
 * `createdAt` ascendente) y se refuerza en [delete], no solo ocultando
 * el botón en la UI.
 */
class ProjectionHistoryViewModel(
    private val budgetRepository: BudgetRepository,
    private val projectionSnapshotRepository: ProjectionSnapshotRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectionHistoryUiState>(ProjectionHistoryUiState.Loading)
    val uiState: StateFlow<ProjectionHistoryUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun load(budgetId: String) {
        job?.cancel()
        _uiState.value = ProjectionHistoryUiState.Loading
        job = viewModelScope.launch {
            val budget = budgetRepository.getById(budgetId)
            if (budget == null) {
                _uiState.value = ProjectionHistoryUiState.NotFound
                return@launch
            }
            projectionSnapshotRepository.observeByBudget(budgetId)
                .mapLatest { snapshots ->
                    val ascending = snapshots.sortedBy { it.createdAt }
                    val entries = ascending.mapIndexed { index, snapshot ->
                        val previous = ascending.getOrNull(index - 1)
                        HistoryEntry(
                            snapshot = snapshot,
                            previewResult = projectSnapshot(snapshot, budget.endDate),
                            diffFromPrevious = previous?.let { ProjectionSnapshotComparer.diff(it, snapshot) },
                            isOriginal = index == 0,
                        )
                    }.sortedByDescending { it.snapshot.createdAt }
                    ProjectionHistoryUiState.Loaded(entries = entries) as ProjectionHistoryUiState
                }
                .collect { state -> _uiState.value = state }
        }
    }

    /**
     * Borra una línea de referencia puntual (agregado en el chat, con
     * confirmación en la UI — ver `ProjectionHistoryScreen`). No hace
     * falta recargar `load()` a mano: `observeByBudget` es reactivo, el
     * `collect` de arriba ya vuelve a emitir sin este snapshot y
     * recalcula los diffs contra el nuevo anterior de cada entrada.
     *
     * No-op si `snapshotId` es la línea original (`HistoryEntry.isOriginal`)
     * — la UI ya oculta el botón para esa entrada, pero se repite la
     * validación acá para no depender únicamente de eso.
     */
    fun delete(snapshotId: String) {
        val state = _uiState.value
        if (state is ProjectionHistoryUiState.Loaded) {
            val entry = state.entries.find { it.snapshot.id == snapshotId }
            if (entry?.isOriginal == true) return
        }
        viewModelScope.launch {
            projectionSnapshotRepository.delete(snapshotId)
        }
    }

    private fun projectSnapshot(snapshot: ProjectionSnapshot, endDate: LocalDate?): ProjectionResult =
        ProjectionEngine.project(
            startDate = snapshot.frozenStartDate,
            endDate = endDate,
            initialBalance = snapshot.frozenInitialBalance,
            template = snapshot.frozenPlannedItems
                .filter { it.isActive }
                .map { frozen ->
                    ProjectionTemplateItem(
                        plannedItemId = frozen.plannedItemId,
                        type = frozen.type,
                        amount = frozen.amount,
                        frequency = frozen.frequency,
                        billingDay = frozen.billingDay,
                        specificDate = frozen.specificDate,
                        dayOfWeek = frozen.dayOfWeek,
                    )
                },
            // today = null a propósito: cada snapshot es 100% plantilla
            // congelada, igual que la línea de referencia del Dashboard.
        )
}

data class HistoryEntry(
    val snapshot: ProjectionSnapshot,
    val previewResult: ProjectionResult,
    /** `null` para el snapshot más viejo de la lista — no hay uno anterior con el que compararlo. */
    val diffFromPrevious: ProjectionSnapshotDiff?,
    /** La línea autogenerada al crear el presupuesto — no se puede borrar (ver KDoc de la clase). */
    val isOriginal: Boolean,
)

sealed interface ProjectionHistoryUiState {
    data object Loading : ProjectionHistoryUiState
    data object NotFound : ProjectionHistoryUiState
    data class Loaded(val entries: List<HistoryEntry>) : ProjectionHistoryUiState
}
