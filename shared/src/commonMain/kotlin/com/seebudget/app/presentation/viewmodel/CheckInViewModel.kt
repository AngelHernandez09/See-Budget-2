package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.projection.CheckInDayBuilder
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.repository.PlannedItemRepository
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * RF-10 — Check-in diario (sección 8.4 #7). Arma el día vía
 * `CheckInDayBuilder` (misma regla de ocurrencia que el Dashboard, ver su
 * KDoc) y expone las acciones de esa pantalla: confirmar/desmarcar un
 * ítem planificado y editar su monto solo para ese día.
 *
 * **Confirmar = crear un `Expense`** vinculado a ese `plannedItemId` y
 * fecha. Usa la `currency` del `PlannedItem` tal cual (sin conversión —
 * mismo criterio que "Registrar gasto", que tampoco fuerza una moneda
 * al vincular un `PlannedItem`, ver `ExpenseFormViewModel`) y su
 * `categoryId` (Fase 7 — ver KDoc de `PlannedItem.kt` sobre por qué
 * existe ese campo). **Un `PlannedItem` sin `categoryId` todavía asignado
 * (creado antes de Fase 7) no se puede confirmar** — [CheckInItemUiState
 * .canConfirm] queda en `false` para que la UI lo deshabilite/marque, en
 * vez de reventar o inventar una categoría.
 *
 * **Desmarcar = borrar el `Expense`** (decisión tomada en el chat):
 * simétrico a confirmar, tombstone igual que cualquier borrado de gasto
 * (ver `ExpenseRepository.delete`). Si se vuelve a confirmar después, se
 * crea un `Expense` nuevo.
 *
 * **Monto editable "solo para ese día"** (RF-10): antes de confirmar, el
 * monto que se edita es un borrador puramente local ([draftAmounts],
 * nunca toca `PlannedItem.amount`) que se descarta si se cambia de día
 * sin confirmar. Una vez confirmado, el campo edita directamente el
 * `Expense` ya persistido (`onAmountChange` detecta cuál de los dos casos
 * aplica por `confirmedExpense != null`) — así queda cubierto "editable
 * retroactivamente" (RF-10: "agregar, modificar o eliminar registros de
 * cualquier día anterior"). En ambos casos, [CheckInItemUiState
 * .differsFromBase] compara contra `plannedItem.amount` para el
 * indicador visual que pide el documento ("indicador si difiere del
 * valor base").
 *
 * **Gastos adicionales del día** (`plannedItemId == null`): esta clase
 * solo los expone en [CheckInUiState.Loaded.additionalExpenses] — crear/
 * editar/eliminar uno reutiliza el flujo existente de "Registrar/Editar
 * gasto" (pantalla 2, `ExpenseFormScreen`/`ExpenseFormViewModel`) con
 * `budgetId` = el de este Check-in, `date` = [CheckInUiState.Loaded.date]
 * y `plannedItemId = null` — no se duplica esa lógica acá.
 *
 * **Pendiente, no resuelto en esta clase:** el "Resumen del día: total
 * confirmado vs. planificado" del documento requeriría sumar montos que
 * pueden estar en monedas distintas entre sí (cada `PlannedItem` tiene su
 * propia `currency`) — igual que `BudgetDashboardViewModel`, sumar eso
 * sin convertir a una moneda común daría un total sin sentido. Queda para
 * cuando se construya la UI de esta pantalla (capa siguiente), con el
 * mismo mecanismo de conversión + `unconvertedCount` que ya usan
 * `BudgetDashboardViewModel`/`ReportsViewModel`.
 */
class CheckInViewModel(
    private val budgetRepository: BudgetRepository,
    private val plannedItemRepository: PlannedItemRepository,
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val _uiState = MutableStateFlow<CheckInUiState>(CheckInUiState.Loading)
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    /**
     * Borradores de monto para ítems TODAVÍA no confirmados de la fecha
     * actualmente visible — ver KDoc de la clase. `plannedItemId -> monto`.
     */
    private val draftAmounts = MutableStateFlow<Map<String, Long>>(emptyMap())

    private var budgetId: String? = null
    private var selectedDate: LocalDate = today
    private var observeJob: Job? = null

    fun load(budgetId: String, date: LocalDate = today) {
        this.budgetId = budgetId
        this.selectedDate = date
        draftAmounts.value = emptyMap()
        observe()
    }

    /** "Selector de fecha... navegable a días anteriores" (RF-10). Descarta los montos sin confirmar del día que se deja — ver KDoc de la clase. */
    fun previousDay() = goToDate(selectedDate.minus(1, DateTimeUnit.DAY))

    fun nextDay() = goToDate(selectedDate.plus(1, DateTimeUnit.DAY))

    fun goToDate(date: LocalDate) {
        selectedDate = date
        draftAmounts.value = emptyMap()
        observe()
    }

    private fun observe() {
        val id = budgetId ?: return
        val date = selectedDate
        observeJob?.cancel()
        _uiState.value = CheckInUiState.Loading
        observeJob = viewModelScope.launch {
            val budget = budgetRepository.getById(id)
            if (budget == null) {
                _uiState.value = CheckInUiState.NotFound
                return@launch
            }
            combine(
                plannedItemRepository.observeByBudget(id),
                expenseRepository.observeByBudgetAndDate(id, date),
                draftAmounts,
            ) { plannedItems, expenses, drafts -> Triple(plannedItems, expenses, drafts) }
                .mapLatest { (plannedItems, expenses, drafts) ->
                    val day = CheckInDayBuilder.build(
                        date = date,
                        activePlannedItems = plannedItems.filter { it.isActive },
                        expensesForDay = expenses,
                    )
                    CheckInUiState.Loaded(
                        budget = budget,
                        date = date,
                        items = day.items.map { item ->
                            CheckInItemUiState(
                                plannedItem = item.plannedItem,
                                confirmedExpense = item.confirmedExpense,
                                editedAmount = item.confirmedExpense?.amount
                                    ?: drafts[item.plannedItem.id]
                                    ?: item.plannedItem.amount,
                            )
                        },
                        additionalExpenses = day.additionalExpenses,
                    )
                }
                .collect { state -> _uiState.value = state }
        }
    }

    /**
     * Campo editable de monto (RF-10). Sin confirmar: solo actualiza el
     * borrador local. Ya confirmado: edita directamente el `Expense`
     * persistido (retroactivo, ver KDoc de la clase) y recalcula
     * `isPlannedOverride` según si el nuevo monto difiere del `PlannedItem`.
     */
    fun onAmountChange(item: CheckInItemUiState, amount: Long) {
        val confirmedExpense = item.confirmedExpense
        if (confirmedExpense == null) {
            draftAmounts.update { it + (item.plannedItem.id to amount) }
        } else {
            viewModelScope.launch {
                expenseRepository.update(
                    confirmedExpense.copy(
                        amount = amount,
                        isPlannedOverride = amount != item.plannedItem.amount,
                    ),
                )
            }
        }
    }

    /** Checkbox: confirma el ítem creando su `Expense`. No hace nada si [CheckInItemUiState.canConfirm] es `false` (falta categoría) o ya está confirmado. */
    fun confirm(item: CheckInItemUiState) {
        val budgetId = this.budgetId ?: return
        val categoryId = item.plannedItem.categoryId ?: return
        if (item.isConfirmed) return
        val date = selectedDate
        val plannedItem = item.plannedItem
        val amount = item.editedAmount
        viewModelScope.launch {
            expenseRepository.create(
                amount = amount,
                currency = plannedItem.currency,
                categoryId = categoryId,
                date = date,
                budgetId = budgetId,
                plannedItemId = plannedItem.id,
                isPlannedOverride = amount != plannedItem.amount,
                type = plannedItem.type,
            )
            // El monto ahora vive en el Expense recién creado, no hace
            // falta seguir arrastrando el borrador para este ítem.
            draftAmounts.update { it - plannedItem.id }
        }
    }

    /** Checkbox: desmarca un ítem ya confirmado, borrando su `Expense` (ver KDoc de la clase). No hace nada si no está confirmado. */
    fun unconfirm(item: CheckInItemUiState) {
        val expense = item.confirmedExpense ?: return
        viewModelScope.launch {
            expenseRepository.delete(expense.id)
        }
    }
}

sealed interface CheckInUiState {
    data object Loading : CheckInUiState
    data object NotFound : CheckInUiState

    data class Loaded(
        val budget: Budget,
        val date: LocalDate,
        val items: List<CheckInItemUiState>,
        /** Gastos ya registrados este día con `plannedItemId == null` — ver KDoc de la clase sobre cómo se crean/editan. */
        val additionalExpenses: List<Expense>,
    ) : CheckInUiState {
        val confirmedCount: Int get() = items.count { it.isConfirmed }
        val totalCount: Int get() = items.size
    }
}

/**
 * Un `PlannedItem` del día junto con su estado de confirmación y el
 * monto que muestra/edita el campo de esta pantalla — ver KDoc de
 * [CheckInViewModel] sobre de dónde sale [editedAmount] en cada caso.
 */
data class CheckInItemUiState(
    val plannedItem: PlannedItem,
    val confirmedExpense: Expense?,
    val editedAmount: Long,
) {
    val isConfirmed: Boolean get() = confirmedExpense != null

    /** `false` si el `PlannedItem` todavía no tiene `categoryId` asignado (ítems creados antes de Fase 7) — ver KDoc de la clase. */
    val canConfirm: Boolean get() = plannedItem.categoryId != null

    /** Para el indicador "difiere del valor base" que pide el documento. */
    val differsFromBase: Boolean get() = editedAmount != plannedItem.amount
}
