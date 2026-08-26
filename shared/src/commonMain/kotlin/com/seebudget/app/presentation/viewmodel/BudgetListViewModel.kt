package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * RF-08 — "Presupuestos — Lista" (sección 8.4 #5): presupuesto ACTIVE
 * destacado + secciones Pausados/Completados. El orden ya viene resuelto
 * desde `BudgetRepository.observeAll()` (ACTIVE primero, ver Budget.sq) —
 * acá solo se separa en 3 listas para que la UI pinte secciones distintas.
 *
 * Las acciones (`activate`/`complete`/`delete`) delegan toda la lógica de
 * ciclo de vida a `BudgetRepository` (Fase 5, capa de persistencia) — este
 * ViewModel no decide nada de negocio, solo dispara y deja que el Flow de
 * `observeAll()` refleje el resultado.
 */
class BudgetListViewModel(
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    val uiState: StateFlow<BudgetListUiState> = budgetRepository.observeAll()
        .map { budgets ->
            BudgetListUiState(
                active = budgets.firstOrNull { it.status == BudgetStatus.ACTIVE },
                paused = budgets.filter { it.status == BudgetStatus.PAUSED },
                completed = budgets.filter { it.status == BudgetStatus.COMPLETED },
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetListUiState(isLoading = true))

    fun activate(id: String) {
        viewModelScope.launch { budgetRepository.activate(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { budgetRepository.delete(id) }
    }
}

data class BudgetListUiState(
    val active: Budget? = null,
    val paused: List<Budget> = emptyList(),
    val completed: List<Budget> = emptyList(),
    val isLoading: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && active == null && paused.isEmpty() && completed.isEmpty()
}
