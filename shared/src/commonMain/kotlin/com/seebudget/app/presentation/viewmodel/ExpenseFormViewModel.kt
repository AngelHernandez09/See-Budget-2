package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.repository.PlannedItemRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * RF-01 — Registrar/editar un gasto (sección 8.4 #3 del documento).
 *
 * `currency` (Fase 3): al crear un gasto nuevo, el default ya no es "USD"
 * hardcodeado — se toma `User.baseCurrency` del perfil del usuario logueado
 * (ver `init`). Si por algún motivo esa fila todavía no existe localmente
 * (carrera con `SyncTrigger.ensureProfile()`, ver App.kt/SyncTrigger.kt),
 * se mantiene "USD" como último fallback. El usuario puede igual elegir
 * cualquier otra moneda del catálogo con `onCurrencyChange` (RF-04, "puede
 * registrar gastos en otras monedas").
 *
 * `budgetId`/`plannedItemId`/`isPlannedOverride` (Fase 6 — RF-08/RF-09):
 * "Selector de presupuesto (solo si hay uno activo)" del documento
 * (sección 8.4 #2). Solo se puede vincular al presupuesto ACTIVE actual
 * (no a cualquier presupuesto histórico) — ver `activeBudget`. Si el
 * usuario elige un `PlannedItem` puntual, `isPlannedOverride` se calcula
 * solo (no es un campo que el usuario toque): es `true` si el monto o la
 * moneda cargados difieren de los del `PlannedItem` original.
 *
 * Nota de alcance (Fase 6, sin Fase 7/check-in todavía): esta es hoy la
 * ÚNICA forma de que un gasto quede vinculado a un presupuesto — no hay
 * still ningún flujo de "confirmar" un `PlannedItem` del día. Por eso el
 * `ProjectionEngine` (ver ProjectionEngine.kt) trata como "real" *solo*
 * lo que efectivamente se cargó acá como `Expense`, y no asume que un
 * `PlannedItem` de tipo INCOME se haya cobrado un día pasado solo porque
 * estaba planificado — ver limitación documentada en ProjectionEngine.kt.
 *
 * `receiptImageUrl` de Expense no forma parte de este formulario básico
 * (Fase 1); al crear queda en null vía los defaults de
 * ExpenseRepository.create(), y al editar se preserva tal cual desde el
 * Expense original.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseFormViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val authRepository: AuthRepository,
    private val budgetRepository: BudgetRepository,
    private val plannedItemRepository: PlannedItemRepository,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null si no hay ningún presupuesto ACTIVE — la sección de vínculo no se muestra. */
    val activeBudget: StateFlow<Budget?> = budgetRepository.observeAll()
        .map { budgets -> budgets.firstOrNull { it.status == BudgetStatus.ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ítems planificados activos del presupuesto ACTIVE (para el selector "¿a qué ítem corresponde?"). */
    val activePlannedItems: StateFlow<List<PlannedItem>> = activeBudget
        .flatMapLatest { budget ->
            if (budget == null) flowOf(emptyList()) else plannedItemRepository.observeByBudget(budget.id)
        }
        .map { items -> items.filter { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ExpenseFormUiState())
    val uiState: StateFlow<ExpenseFormUiState> = _uiState.asStateFlow()

    init {
        loadDefaultCurrencyIfNeeded()
    }

    private fun loadDefaultCurrencyIfNeeded() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId() ?: return@launch
            val baseCurrency = userSettingsRepository.observe(userId).first()?.baseCurrency ?: return@launch
            // Solo pisa el default si todavía es "crear nuevo" y nadie tocó
            // la moneda a mano todavía — si en el medio se llamó
            // `loadForEdit` o el usuario ya eligió otra, no corresponde.
            _uiState.update { state ->
                if (state.editingId == null && !state.currencyTouched) {
                    state.copy(currency = baseCurrency)
                } else {
                    state
                }
            }
        }
    }

    /**
     * Vuelve al estado "registrar gasto" en blanco. Necesario porque
     * `koinViewModel()` puede devolver esta MISMA instancia en distintas
     * navegaciones (esta app no usa Navigation Compose, así que el
     * `ViewModelStore` no se destruye al "salir" de la pantalla) — sin
     * este reset, tocar "Registrar gasto" después de haber editado uno
     * seguía mostrando y guardando sobre el gasto anterior (mismo bug ya
     * corregido en `BudgetFormViewModel`, reportado en el chat). Llamar
     * desde `ExpenseFormScreen` cuando `expenseId == null`, como
     * contraparte de `loadForEdit`.
     */
    fun reset() {
        _uiState.value = ExpenseFormUiState()
        loadDefaultCurrencyIfNeeded()
    }

    /**
     * "+ Agregar gasto" desde el Check-in diario (RF-10, Fase 7): arranca
     * ya vinculado a ese presupuesto y con la fecha del día que se está
     * revisando (en vez de "hoy", default de [reset]) — `plannedItemId`
     * queda `null` a propósito: es exactamente el caso "gasto adicional
     * no planificado" (RF-10). Llamar desde `ExpenseFormScreen` en vez de
     * [reset] cuando la pantalla se abre con `prefillBudgetId`/
     * `prefillDate` (ver `App.kt`).
     */
    fun prefillForCheckIn(budgetId: String, date: LocalDate) {
        _uiState.value = ExpenseFormUiState(date = date, budgetId = budgetId, plannedItemId = null)
        loadDefaultCurrencyIfNeeded()
    }

    /** Carga un gasto existente para editar. No llamar para "crear nuevo". */
    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val expense = expenseRepository.getById(id) ?: return@launch
            _uiState.update {
                ExpenseFormUiState(
                    editingId = expense.id,
                    amount = expense.amount,
                    currency = expense.currency,
                    currencyTouched = true,
                    categoryId = expense.categoryId,
                    date = expense.date,
                    note = expense.note.orEmpty(),
                    paymentMethod = expense.paymentMethod.orEmpty(),
                    budgetId = expense.budgetId,
                    plannedItemId = expense.plannedItemId,
                    type = expense.type,
                )
            }
        }
    }

    fun onAmountChange(amount: Long) {
        _uiState.update { it.copy(amount = amount) }
    }

    fun onCurrencyChange(currency: String) {
        _uiState.update { it.copy(currency = currency, currencyTouched = true) }
    }

    fun onCategoryChange(categoryId: String) {
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    /**
     * Ingreso/Egreso (agregado en el chat — antes `Expense.type` solo se
     * fijaba desde el Check-in diario, ver KDoc de `ExpenseRepository
     * .create()`). Sin efecto si el gasto está vinculado a un
     * `PlannedItem` con su propio `type` — ver `onPlannedItemChange`.
     */
    fun onTypeChange(type: PlannedItemType) {
        _uiState.update { it.copy(type = type) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onPaymentMethodChange(paymentMethod: String) {
        _uiState.update { it.copy(paymentMethod = paymentMethod) }
    }

    /** Prende/apaga el vínculo con el presupuesto ACTIVE. Al apagar, también limpia `plannedItemId`. */
    fun onLinkedToBudgetChange(linked: Boolean) {
        val budgetId = if (linked) activeBudget.value?.id else null
        _uiState.update { it.copy(budgetId = budgetId, plannedItemId = null) }
    }

    /**
     * `null` = "gasto adicional" (vinculado al presupuesto pero sin ítem
     * planificado de origen).
     *
     * **Categoría forzada** (Fase 7, decisión tomada en el chat): si el
     * `PlannedItem` elegido ya tiene `categoryId` asignado (ver KDoc de
     * `PlannedItem.kt`), la categoría del gasto pasa a ser la suya —
     * mismo criterio rígido que ya usa el Check-in diario al confirmar
     * (`CheckInViewModel.confirm`), para que un gasto vinculado a un
     * ítem nunca quede en una categoría distinta a la que ese ítem tiene
     * definida. Si el `PlannedItem` todavía no tiene categoría (creado
     * antes de Fase 7), la categoría del formulario queda libre como
     * hasta ahora. La UI (`CategoryPicker`) deshabilita el selector en el
     * primer caso — ver `ExpenseFormScreen.categoryLocked`.
     *
     * **Tipo forzado** (agregado en el chat, mismo criterio que la
     * categoría): a diferencia de `categoryId`, `PlannedItem.type` nunca
     * es nulo, así que acá SIEMPRE se fuerza al elegir un ítem — un gasto
     * vinculado a un `PlannedItem` de ingreso no puede quedar marcado
     * como egreso (o viceversa). Al desvincular (`plannedItemId = null`)
     * el tipo queda como estaba, ahora libre para elegir a mano.
     */
    fun onPlannedItemChange(plannedItemId: String?) {
        val linkedItem = plannedItemId?.let { id -> activePlannedItems.value.firstOrNull { it.id == id } }
        _uiState.update { state ->
            state.copy(
                plannedItemId = plannedItemId,
                categoryId = linkedItem?.categoryId ?: state.categoryId,
                type = linkedItem?.type ?: state.type,
            )
        }
    }

    /** Crea si `editingId` es null; si no, actualiza ese gasto. */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            val plannedItem = state.plannedItemId?.let { plannedItemRepository.getById(it) }
            val isPlannedOverride = plannedItem != null &&
                (plannedItem.amount != state.amount || plannedItem.currency != state.currency)

            val editingId = state.editingId
            if (editingId == null) {
                expenseRepository.create(
                    amount = state.amount,
                    currency = state.currency,
                    categoryId = state.categoryId,
                    date = state.date,
                    note = state.note.ifBlank { null },
                    paymentMethod = state.paymentMethod.ifBlank { null },
                    budgetId = state.budgetId,
                    plannedItemId = state.plannedItemId,
                    isPlannedOverride = isPlannedOverride,
                    type = state.type,
                )
            } else {
                val current = expenseRepository.getById(editingId) ?: return@launch
                expenseRepository.update(
                    current.copy(
                        amount = state.amount,
                        currency = state.currency,
                        categoryId = state.categoryId,
                        date = state.date,
                        note = state.note.ifBlank { null },
                        paymentMethod = state.paymentMethod.ifBlank { null },
                        budgetId = state.budgetId,
                        plannedItemId = state.plannedItemId,
                        isPlannedOverride = isPlannedOverride,
                        type = state.type,
                    )
                )
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch {
            expenseRepository.delete(id)
            onDeleted()
        }
    }
}

data class ExpenseFormUiState(
    val editingId: String? = null,
    val amount: Long = 0L,
    // Fase 3: default real es User.baseCurrency, aplicado async en `init`
    // (ver comentario de la clase) — "USD" acá es solo el fallback antes
    // de que resuelva esa carga o si no hay perfil todavía.
    val currency: String = "USD",
    // true apenas el usuario elige una moneda a mano o se carga un gasto
    // existente — evita que el fetch de baseCurrency en `init` pise una
    // elección ya hecha (ver `init`/`onCurrencyChange`).
    val currencyTouched: Boolean = false,
    val categoryId: String = "",
    val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val note: String = "",
    val paymentMethod: String = "",
    // Fase 6 — ver KDoc de la clase.
    val budgetId: String? = null,
    val plannedItemId: String? = null,
    // Agregado en el chat — ver KDoc de onTypeChange/onPlannedItemChange.
    // Default EXPENSE: mismo comportamiento de siempre para quien no lo toca.
    val type: PlannedItemType = PlannedItemType.EXPENSE,
)
