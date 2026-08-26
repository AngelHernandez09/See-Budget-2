package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.projection.ProjectionSnapshotFactory
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.repository.PlannedItemRepository
import com.seebudget.app.domain.repository.ProjectionSnapshotRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * RF-08/RF-09 — "Crear/Editar presupuesto" (sección 8.4 #9), con la
 * sección "Ítems planificados" embebida (mismo formulario hace también de
 * "Ajustes del presupuesto", #10 — misma info editable, ver
 * BudgetFormScreen).
 *
 * **Diálogo "¿corrección de error o cambio real?"** (RF-09, agregado en
 * el chat — Fase 6 cierre): dispara en CADA edición que afecta la
 * proyección de un presupuesto que YA tiene al menos un
 * `ProjectionSnapshot` (si no tiene ninguno todavía, no hay nada que
 * preservar, así que no se pregunta nada):
 * - Alta/edición/baja de un `PlannedItem` guardado (`addPlannedItem`/
 *   `updatePlannedItem`/`deletePlannedItem`, rama `Saved`).
 * - `save()` cuando cambia `initialBalance` o `startDate` (no otros
 *   campos — `endDate`/`baseCurrency`/nombre/notificación no afectan la
 *   plantilla congelada, ver KDoc de `ProjectionSnapshot`).
 *
 * El cambio real (el `PlannedItem`, o el `Budget`) SIEMPRE se persiste de
 * inmediato — el diálogo es sobre qué hacer con la línea de referencia
 * `ProjectionSnapshot`, no sobre si aplicar el cambio. Al resolverse
 * queda `pendingSnapshotDecision = false` en el estado y, si el trigger
 * vino de `save()`, recién ahí se llama al `onSaved` que había quedado
 * pendiente (para no navegar fuera de la pantalla antes de que el
 * usuario vea el diálogo).
 *
 * Tres resoluciones posibles (UI, `BudgetFormScreen`):
 * - **Corrección de error** ([confirmCorrection]): sobreescribe el
 *   snapshot más reciente en el lugar (`ProjectionSnapshotRepository
 *   .update()`) con el estado vigente — no genera una línea nueva.
 * - **Cambio real, mantener línea actual** ([confirmKeepCurrentLine]):
 *   no toca ningún snapshot — la línea de referencia sigue como estaba,
 *   ahora divergiendo a propósito de la real.
 * - **Cambio real, generar nueva línea** ([confirmNewLine]): crea un
 *   `ProjectionSnapshot` nuevo (`create()`) con el estado vigente y el
 *   label que escriba el usuario.
 *
 * [createNewReferenceLine] es el botón manual "Crear nueva línea de
 * referencia desde hoy" (documento, pantalla 10) — mismo mecanismo que
 * "generar nueva línea" de arriba, pero disponible en cualquier momento,
 * sin pasar por el diálogo de corrección/cambio real.
 *
 * `baseCurrency` (al crear): mismo patrón que `ExpenseFormViewModel` — el
 * default es `User.baseCurrency`, resuelto async en `init`, con guard
 * (`baseCurrencyTouched`) para no pisar una elección manual.
 *
 * PlannedItem en modo "crear presupuesto": todavía no hay `budgetId` real
 * hasta que se guarda el presupuesto, así que los ítems que el usuario
 * agrega quedan como `PlannedItemRow.Draft` en memoria (no se persisten)
 * y recién se crean de verdad en `save()`, después de que el presupuesto
 * ya tiene id. En modo "editar", cada alta/edición/baja de ítem es
 * inmediata contra `PlannedItemRepository` (no hay "borrador" — ya existe
 * el presupuesto dueño).
 */
class BudgetFormViewModel(
    private val budgetRepository: BudgetRepository,
    private val plannedItemRepository: PlannedItemRepository,
    private val categoryRepository: CategoryRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val authRepository: AuthRepository,
    private val projectionSnapshotRepository: ProjectionSnapshotRepository,
    private val projectionSnapshotFactory: ProjectionSnapshotFactory,
) : ViewModel() {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Selector de categoría del sub-formulario de ítems planificados (Fase 7, RF-10) — ver KDoc de `PlannedItem.kt`. */
    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(BudgetFormUiState())
    val uiState: StateFlow<BudgetFormUiState> = _uiState.asStateFlow()

    private var plannedItemsJob: Job? = null

    /** `onSaved` de `save()` cuando queda esperando la resolución del diálogo — ver KDoc de la clase. */
    private var pendingOnSaved: (() -> Unit)? = null

    init {
        loadDefaultBaseCurrencyIfNeeded()
    }

    private fun loadDefaultBaseCurrencyIfNeeded() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId() ?: return@launch
            val baseCurrency = userSettingsRepository.observe(userId).first()?.baseCurrency ?: return@launch
            _uiState.update { state ->
                if (state.editingId == null && !state.baseCurrencyTouched) {
                    state.copy(baseCurrency = baseCurrency)
                } else {
                    state
                }
            }
        }
    }

    /**
     * Vuelve al estado "crear presupuesto" en blanco. Necesario porque
     * `koinViewModel()` puede devolver esta MISMA instancia en distintas
     * navegaciones (esta app no usa Navigation Compose, así que el
     * `ViewModelStore` no se destruye al "salir" de la pantalla) — sin
     * este reset, tocar "+ Nuevo" después de haber editado un presupuesto
     * seguía mostrando y guardando sobre ese presupuesto anterior (bug
     * reportado en el chat). Llamar desde `BudgetFormScreen` cuando
     * `budgetId == null`, como contraparte de `loadForEdit`.
     */
    fun reset() {
        plannedItemsJob?.cancel()
        plannedItemsJob = null
        pendingOnSaved = null
        _uiState.value = BudgetFormUiState()
        loadDefaultBaseCurrencyIfNeeded()
    }

    /** Carga un presupuesto existente para editar. No llamar para "crear nuevo". */
    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val budget = budgetRepository.getById(id) ?: return@launch
            _uiState.update {
                BudgetFormUiState(
                    editingId = budget.id,
                    status = budget.status,
                    name = budget.name,
                    startDate = budget.startDate,
                    hasEndDate = budget.endDate != null,
                    endDate = budget.endDate ?: budget.startDate,
                    initialBalance = budget.initialBalance,
                    baseCurrency = budget.baseCurrency,
                    baseCurrencyTouched = true,
                    notificationEnabled = budget.notificationTime != null,
                    notificationTime = budget.notificationTime ?: LocalTime(9, 0),
                )
            }
            plannedItemsJob?.cancel()
            plannedItemsJob = viewModelScope.launch {
                plannedItemRepository.observeByBudget(id).collect { items ->
                    _uiState.update { it.copy(plannedItems = items.map(PlannedItemRow::Saved)) }
                }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }
    fun onStartDateChange(date: LocalDate) = _uiState.update { it.copy(startDate = date) }
    fun onHasEndDateChange(hasEndDate: Boolean) = _uiState.update { it.copy(hasEndDate = hasEndDate) }
    fun onEndDateChange(date: LocalDate) = _uiState.update { it.copy(endDate = date) }
    fun onInitialBalanceChange(amount: Long) = _uiState.update { it.copy(initialBalance = amount) }
    fun onBaseCurrencyChange(currency: String) =
        _uiState.update { it.copy(baseCurrency = currency, baseCurrencyTouched = true) }
    fun onNotificationEnabledChange(enabled: Boolean) = _uiState.update { it.copy(notificationEnabled = enabled) }
    fun onNotificationTimeChange(time: LocalTime) = _uiState.update { it.copy(notificationTime = time) }

    /** Modo crear: queda en borrador (no dispara el diálogo — recién existe presupuesto al guardar). Modo editar: se persiste ya mismo y puede disparar el diálogo. */
    fun addPlannedItem(draft: PlannedItemDraft) {
        val budgetId = _uiState.value.editingId
        if (budgetId == null) {
            _uiState.update { it.copy(plannedItems = it.plannedItems + PlannedItemRow.Draft(draft)) }
        } else {
            viewModelScope.launch {
                plannedItemRepository.create(
                    budgetId = budgetId,
                    name = draft.name,
                    categoryId = draft.categoryId,
                    type = draft.type,
                    amount = draft.amount,
                    currency = draft.currency,
                    frequency = draft.frequency,
                    billingDay = draft.billingDay,
                    specificDate = draft.specificDate,
                    dayOfWeek = draft.dayOfWeek,
                    isActive = draft.isActive,
                )
                requestSnapshotDecisionIfNeeded(budgetId)
            }
        }
    }

    fun updatePlannedItem(row: PlannedItemRow, draft: PlannedItemDraft) {
        when (row) {
            is PlannedItemRow.Draft -> _uiState.update { state ->
                state.copy(plannedItems = state.plannedItems.map { if (it === row) PlannedItemRow.Draft(draft) else it })
            }
            is PlannedItemRow.Saved -> viewModelScope.launch {
                plannedItemRepository.update(
                    row.item.copy(
                        name = draft.name,
                        categoryId = draft.categoryId,
                        type = draft.type,
                        amount = draft.amount,
                        currency = draft.currency,
                        frequency = draft.frequency,
                        billingDay = draft.billingDay,
                        specificDate = draft.specificDate,
                        dayOfWeek = draft.dayOfWeek,
                        isActive = draft.isActive,
                    )
                )
                requestSnapshotDecisionIfNeeded(row.item.budgetId)
            }
        }
    }

    fun deletePlannedItem(row: PlannedItemRow) {
        when (row) {
            is PlannedItemRow.Draft -> _uiState.update { state ->
                state.copy(plannedItems = state.plannedItems.filterNot { it === row })
            }
            is PlannedItemRow.Saved -> viewModelScope.launch {
                plannedItemRepository.delete(row.item.id)
                requestSnapshotDecisionIfNeeded(row.item.budgetId)
            }
        }
    }

    /** Crea si `editingId` es null; si no, actualiza ese presupuesto (sin tocar `status`). */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            val editingId = state.editingId
            val endDate = if (state.hasEndDate) state.endDate else null
            val notificationTime = if (state.notificationEnabled) state.notificationTime else null
            if (editingId == null) {
                val budget = budgetRepository.create(
                    name = state.name,
                    startDate = state.startDate,
                    endDate = endDate,
                    initialBalance = state.initialBalance,
                    baseCurrency = state.baseCurrency,
                    notificationTime = notificationTime,
                )
                // Recién acá existe budgetId real: se persisten los ítems
                // que quedaron en borrador (ver KDoc de la clase). Nunca
                // dispara el diálogo — un presupuesto recién creado no
                // tiene snapshots todavía.
                for (row in state.plannedItems.filterIsInstance<PlannedItemRow.Draft>()) {
                    plannedItemRepository.create(
                        budgetId = budget.id,
                        name = row.draft.name,
                        categoryId = row.draft.categoryId,
                        type = row.draft.type,
                        amount = row.draft.amount,
                        currency = row.draft.currency,
                        frequency = row.draft.frequency,
                        billingDay = row.draft.billingDay,
                        specificDate = row.draft.specificDate,
                        dayOfWeek = row.draft.dayOfWeek,
                        isActive = row.draft.isActive,
                    )
                }
                onSaved()
            } else {
                val current = budgetRepository.getById(editingId) ?: return@launch
                val balanceOrDateChanged = current.initialBalance != state.initialBalance || current.startDate != state.startDate
                budgetRepository.update(
                    current.copy(
                        name = state.name,
                        startDate = state.startDate,
                        endDate = endDate,
                        initialBalance = state.initialBalance,
                        baseCurrency = state.baseCurrency,
                        notificationTime = notificationTime,
                    )
                )
                val triggered = balanceOrDateChanged && requestSnapshotDecisionIfNeeded(editingId)
                if (triggered) {
                    // No navegamos todavía — se llama a onSaved recién
                    // cuando el usuario resuelva el diálogo (ver KDoc).
                    pendingOnSaved = onSaved
                } else {
                    onSaved()
                }
            }
        }
    }

    /** `true` si había un snapshot al que preguntarle qué hacer (y por lo tanto se puso `pendingSnapshotDecision = true`). */
    private suspend fun requestSnapshotDecisionIfNeeded(budgetId: String): Boolean {
        val hasSnapshot = projectionSnapshotRepository.getLatest(budgetId) != null
        if (hasSnapshot) {
            _uiState.update { it.copy(pendingSnapshotDecision = true) }
        }
        return hasSnapshot
    }

    /** "Corrección de error": sobreescribe el snapshot más reciente en el lugar — ver KDoc de la clase. */
    fun confirmCorrection() {
        val budgetId = _uiState.value.editingId ?: return
        viewModelScope.launch {
            val latest = projectionSnapshotRepository.getLatest(budgetId) ?: return@launch
            val budget = budgetRepository.getById(budgetId) ?: return@launch
            val activeItems = plannedItemRepository.observeByBudget(budgetId).first().filter { it.isActive }
            val frozen = projectionSnapshotFactory.freeze(budget, activeItems, today)
            projectionSnapshotRepository.update(
                latest.copy(
                    frozenInitialBalance = budget.initialBalance,
                    frozenStartDate = budget.startDate,
                    frozenBaseCurrency = budget.baseCurrency,
                    frozenPlannedItems = frozen.items,
                )
            )
            resolveSnapshotDecision()
        }
    }

    /** "Cambio real, mantener línea actual": no toca ningún snapshot. */
    fun confirmKeepCurrentLine() {
        resolveSnapshotDecision()
    }

    /** "Cambio real, generar nueva línea": ver KDoc de la clase / [createNewReferenceLine]. */
    fun confirmNewLine(label: String) {
        val budgetId = _uiState.value.editingId ?: return
        viewModelScope.launch {
            createSnapshotFromCurrentState(budgetId, label)
            resolveSnapshotDecision()
        }
    }

    /**
     * Botón manual "Crear nueva línea de referencia desde hoy" (documento,
     * pantalla 10) — disponible en cualquier momento, independiente del
     * diálogo de corrección/cambio real (no lo dispara ni lo resuelve).
     */
    fun createNewReferenceLine(label: String) {
        val budgetId = _uiState.value.editingId ?: return
        viewModelScope.launch { createSnapshotFromCurrentState(budgetId, label) }
    }

    private suspend fun createSnapshotFromCurrentState(budgetId: String, label: String) {
        val budget = budgetRepository.getById(budgetId) ?: return
        val activeItems = plannedItemRepository.observeByBudget(budgetId).first().filter { it.isActive }
        val frozen = projectionSnapshotFactory.freeze(budget, activeItems, today)
        projectionSnapshotRepository.create(
            budgetId = budgetId,
            label = label.ifBlank { "Ajuste $today" },
            frozenInitialBalance = budget.initialBalance,
            frozenStartDate = budget.startDate,
            frozenBaseCurrency = budget.baseCurrency,
            frozenPlannedItems = frozen.items,
        )
    }

    private fun resolveSnapshotDecision() {
        _uiState.update { it.copy(pendingSnapshotDecision = false) }
        val onSaved = pendingOnSaved
        pendingOnSaved = null
        onSaved?.invoke()
    }

    fun activate() {
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch { budgetRepository.activate(id) }
    }

    /** "Pausar" manual (agregado en el chat) — ver KDoc de `BudgetRepository.pause`. */
    fun pause() {
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch { budgetRepository.pause(id) }
    }

    fun complete(onDone: () -> Unit) {
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch {
            budgetRepository.complete(id)
            onDone()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch {
            budgetRepository.delete(id)
            onDeleted()
        }
    }
}

data class PlannedItemDraft(
    val name: String = "",
    // Fase 7 (RF-10) — ver KDoc de PlannedItem.kt.
    val categoryId: String? = null,
    val type: PlannedItemType = PlannedItemType.EXPENSE,
    val amount: Long = 0L,
    val currency: String = "USD",
    val frequency: PlannedItemFrequency = PlannedItemFrequency.MONTHLY,
    val billingDay: Int? = null,
    val specificDate: LocalDate? = null,
    val dayOfWeek: DayOfWeek? = null,
    val isActive: Boolean = true,
)

/**
 * Fila de la sección "Ítems planificados": ya persistida (`Saved`, editar/
 * borrar pega directo contra `PlannedItemRepository`) o todavía en
 * memoria (`Draft`, solo posible mientras se crea un presupuesto nuevo —
 * ver KDoc de la clase).
 */
sealed interface PlannedItemRow {
    data class Saved(val item: PlannedItem) : PlannedItemRow
    data class Draft(val draft: PlannedItemDraft) : PlannedItemRow
}

data class BudgetFormUiState(
    val editingId: String? = null,
    val status: BudgetStatus? = null,
    val name: String = "",
    val startDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val hasEndDate: Boolean = false,
    val endDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val initialBalance: Long = 0L,
    // Fase 5: default real es User.baseCurrency, aplicado async en `init`
    // (mismo patrón que ExpenseFormViewModel) — "USD" acá es solo el
    // fallback antes de que resuelva esa carga.
    val baseCurrency: String = "USD",
    val baseCurrencyTouched: Boolean = false,
    val notificationEnabled: Boolean = false,
    val notificationTime: LocalTime = LocalTime(9, 0),
    val plannedItems: List<PlannedItemRow> = emptyList(),
    /** `true` mientras se espera que el usuario resuelva "¿corrección de error o cambio real?" — ver KDoc de la clase. */
    val pendingSnapshotDecision: Boolean = false,
)
