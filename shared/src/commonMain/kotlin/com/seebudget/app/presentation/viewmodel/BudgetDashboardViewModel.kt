package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.ProjectionSnapshot
import com.seebudget.app.domain.projection.ProjectionEngine
import com.seebudget.app.domain.projection.ProjectionResult
import com.seebudget.app.domain.projection.ProjectionSnapshotFactory
import com.seebudget.app.domain.projection.ProjectionTemplateItem
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.ExchangeRateRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.repository.PlannedItemRepository
import com.seebudget.app.domain.repository.ProjectionSnapshotRepository
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * RF-09 — Presupuesto: Detalle/Dashboard (sección 8, pantalla 6). Arma
 * las dos líneas del gráfico llamando a `ProjectionEngine.project()` dos
 * veces (ver KDoc de esa clase) y expone lo que la UI necesita: saldo de
 * hoy, ambas líneas día a día, la fecha de quiebre de cada una, días
 * restantes y el resumen de gasto proyectado del mes/año en curso.
 *
 * **Snapshot inicial automático** (decisión tomada en el chat, Fase 6):
 * si el presupuesto todavía no tiene ningún `ProjectionSnapshot`, la
 * primera vez que se abre este Dashboard se crea uno con label "Original"
 * a partir del estado vigente — así siempre hay una línea de referencia
 * para comparar, sin que el usuario tenga que generarla a mano.
 *
 * **Conversión de moneda al congelar un snapshot:** un `PlannedItem`
 * puede estar en una moneda distinta a la del presupuesto. Al crear el
 * snapshot, cada ítem se convierte a `baseCurrency` con la tasa de HOY
 * (no hay tasa "futura", ver `ExchangeRateRepositoryImpl`) y esa
 * conversión queda congelada — `ProjectionSnapshotItem.amount` guarda el
 * monto YA convertido, no el original. Es intencional: si se re-
 * convirtiera con la tasa de cada vez que se abre el Dashboard, la línea
 * de referencia se movería sola con las fluctuaciones del tipo de
 * cambio, dejando de servir como referencia fija. `ProjectionSnapshotItem
 * .currency` queda como metadato informativo (moneda original del ítem
 * en ese momento), no se vuelve a usar para convertir.
 *
 * **Ítems sin tasa disponible** (offline y sin nada cacheado, RNF-02: la
 * app nunca debe trabarse por esto): se excluyen de la simulación de ese
 * ítem puntual y se cuentan en `unconvertedTemplateItemCount`/
 * `unconvertedExpenseCount` — mismo criterio que `ReportsViewModel`
 * (`unconvertedCount`), no se inventa una conversión 1:1 que podría ser
 * muy inexacta entre monedas distintas.
 *
 * **`endDate` de la línea de referencia:** el snapshot NO congela
 * `endDate` (ver KDoc de `ProjectionSnapshot`) — ambas líneas usan el
 * `endDate` VIGENTE del presupuesto solo para acotar hasta dónde se
 * grafica; no afecta los montos ni el saldo proyectado de ninguna línea.
 *
 * **Gasto proyectado del mes/año** (agregado en el chat, notas de UI):
 * se calcula sumando `expenseDelta` de `realLine.days` (nunca de la
 * plantilla congelada — el resumen es sobre el estado REAL/vigente) en
 * dos rangos:
 * - `monthly/yearlyProjectedExpense`: el mes/año calendario COMPLETO en
 *   el que cae `today`, combinando gasto real (días `<= today`) y
 *   proyectado con la plantilla vigente (días `> today`) — respuesta
 *   explícita del usuario a "¿el total es solo lo ya gastado o incluye
 *   lo que falta por gastar?": incluye lo que falta, mes/año completo.
 * - `monthly/yearlyRemainingExpense`: SOLO lo que falta gastar desde
 *   `today` (inclusive) hasta fin de mes/año — es la base de comparación
 *   para las banderas rojas (`monthly/yearlyExpenseExceedsBalance`):
 *   "¿el saldo actual alcanza para cubrir lo que resta del período?" —
 *   respuesta explícita del usuario, distinta del total de arriba porque
 *   el saldo actual nunca podría cubrir gasto que ya ocurrió.
 * Si el rango simulado de `realLine` no cubre el mes/año completo (ej.
 * el presupuesto arranca a mitad de mes, o `endDate`/horizonte lo corta
 * antes), la suma solo cuenta los días que sí fueron simulados — no se
 * extrapola ni se avisa aparte, es una limitación conocida y aceptable
 * por ahora.
 *
 * El diálogo "¿corrección de error o cambio real?" al editar la
 * plantilla base o el saldo/fecha de un presupuesto que ya tiene
 * snapshots vive en `BudgetFormViewModel`/`BudgetFormScreen` ("Ajustes
 * del presupuesto"), no acá — esta pantalla es de solo lectura sobre la
 * proyección. `ensureInitialSnapshot()` y ese diálogo comparten la misma
 * lógica de congelado vía `ProjectionSnapshotFactory`.
 *
 * **`varianceVsReference`** (agregado en el chat — "has ahorrado" / "has
 * excedido" en el Dashboard): compara `todayBalance` (línea real) contra
 * el saldo de la línea de referencia en la fecha de HOY — positivo
 * significa que el saldo real de hoy es mayor al que predecía la
 * referencia original (ahorro), negativo que es menor (excedido). `null`
 * si no hay línea de referencia o si su rango simulado no cubre `today`
 * (ej. snapshot congelado con `frozenStartDate` posterior a hoy).
 */
class BudgetDashboardViewModel(
    private val budgetRepository: BudgetRepository,
    private val plannedItemRepository: PlannedItemRepository,
    private val projectionSnapshotRepository: ProjectionSnapshotRepository,
    private val expenseRepository: ExpenseRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val projectionSnapshotFactory: ProjectionSnapshotFactory,
) : ViewModel() {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val _uiState = MutableStateFlow<BudgetDashboardUiState>(BudgetDashboardUiState.Loading)
    val uiState: StateFlow<BudgetDashboardUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun load(budgetId: String) {
        observeJob?.cancel()
        _uiState.value = BudgetDashboardUiState.Loading
        observeJob = viewModelScope.launch {
            val budget = budgetRepository.getById(budgetId)
            if (budget == null) {
                _uiState.value = BudgetDashboardUiState.NotFound
                return@launch
            }
            ensureInitialSnapshot(budget)

            combine(
                plannedItemRepository.observeByBudget(budgetId),
                projectionSnapshotRepository.observeByBudget(budgetId),
                expenseRepository.observeByDateRange(budget.startDate, today),
            ) { plannedItems, snapshots, expenses -> Triple(plannedItems, snapshots, expenses) }
                .mapLatest { (plannedItems, snapshots, expenses) ->
                    buildState(
                        budget = budget,
                        activePlannedItems = plannedItems.filter { it.isActive },
                        latestSnapshot = snapshots.maxByOrNull { it.createdAt },
                        budgetExpenses = expenses.filter { it.budgetId == budgetId },
                    )
                }
                .collect { state -> _uiState.value = state }
        }
    }

    private suspend fun ensureInitialSnapshot(budget: Budget) {
        if (projectionSnapshotRepository.getLatest(budget.id) != null) return
        val activeItems = plannedItemRepository.observeByBudget(budget.id).first().filter { it.isActive }
        val frozen = projectionSnapshotFactory.freeze(budget, activeItems, today)
        projectionSnapshotRepository.create(
            budgetId = budget.id,
            label = "Original",
            frozenInitialBalance = budget.initialBalance,
            frozenStartDate = budget.startDate,
            frozenBaseCurrency = budget.baseCurrency,
            frozenPlannedItems = frozen.items,
        )
    }

    private suspend fun convertAtRateOf(date: LocalDate, amount: Long, from: String, to: String): Long? {
        val rate = exchangeRateRepository.getRate(date, from, to) ?: return null
        return MoneyFormat.convert(amount, from, to, rate)
    }

    private suspend fun buildState(
        budget: Budget,
        activePlannedItems: List<PlannedItem>,
        latestSnapshot: ProjectionSnapshot?,
        budgetExpenses: List<Expense>,
    ): BudgetDashboardUiState.Loaded {
        var unconvertedTemplateItems = 0
        val liveTemplate = activePlannedItems.mapNotNull { item ->
            val convertedAmount = convertAtRateOf(today, item.amount, item.currency, budget.baseCurrency)
            if (convertedAmount == null) {
                unconvertedTemplateItems++
                null
            } else {
                ProjectionTemplateItem(
                    plannedItemId = item.id,
                    type = item.type,
                    amount = convertedAmount,
                    frequency = item.frequency,
                    billingDay = item.billingDay,
                    specificDate = item.specificDate,
                    dayOfWeek = item.dayOfWeek,
                )
            }
        }

        var unconvertedExpenses = 0
        val actualDeltasByDate = HashMap<LocalDate, Long>()
        for (expense in budgetExpenses) {
            val converted = convertAtRateOf(expense.date, expense.amount, expense.currency, budget.baseCurrency)
            if (converted == null) {
                unconvertedExpenses++
                continue
            }
            // Fase 7 — Expense.type cerró el gap de RF-01 ("Expense siempre
            // es un egreso"): INCOME (confirmado desde el Check-in diario,
            // RF-10) suma al saldo real, EXPENSE resta, igual que la
            // plantilla proyectada más abajo en ProjectionEngine.
            val signedDelta = when (expense.type) {
                PlannedItemType.INCOME -> converted
                PlannedItemType.EXPENSE -> -converted
            }
            actualDeltasByDate[expense.date] = (actualDeltasByDate[expense.date] ?: 0L) + signedDelta
        }

        val realLine = ProjectionEngine.project(
            startDate = budget.startDate,
            endDate = budget.endDate,
            initialBalance = budget.initialBalance,
            template = liveTemplate,
            today = today,
            actualDeltasByDate = actualDeltasByDate,
        )

        val referenceLine = latestSnapshot?.let { snapshot ->
            ProjectionEngine.project(
                startDate = snapshot.frozenStartDate,
                endDate = budget.endDate,
                initialBalance = snapshot.frozenInitialBalance,
                template = snapshot.frozenPlannedItems
                    .filter { it.isActive }
                    .map { frozen ->
                        ProjectionTemplateItem(
                            plannedItemId = frozen.plannedItemId,
                            type = frozen.type,
                            amount = frozen.amount, // ya convertido al congelarse, ver KDoc de la clase
                            frequency = frozen.frequency,
                            billingDay = frozen.billingDay,
                            specificDate = frozen.specificDate,
                            dayOfWeek = frozen.dayOfWeek,
                        )
                    },
                // today = null a propósito: la línea de referencia es 100%
                // plantilla congelada, nunca se mezcla con datos reales.
            )
        }

        val todayBalance = realLine.days.firstOrNull { it.date == today }?.balance ?: budget.initialBalance
        val referenceTodayBalance = referenceLine?.days?.firstOrNull { it.date == today }?.balance
        val varianceVsReference = referenceTodayBalance?.let { todayBalance - it }

        val breakEven = realLine.breakEvenDate
        val daysRemaining = breakEven?.let { maxOf(0, today.daysUntil(it)) }

        val monthStart = LocalDate(today.year, today.monthNumber, 1)
        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val yearStart = LocalDate(today.year, 1, 1)
        val yearEnd = LocalDate(today.year, 12, 31)

        val monthlyProjectedExpense = realLine.days.filter { it.date in monthStart..monthEnd }.sumOf { it.expenseDelta }
        val monthlyRemainingExpense = realLine.days.filter { it.date in today..monthEnd }.sumOf { it.expenseDelta }
        val yearlyProjectedExpense = realLine.days.filter { it.date in yearStart..yearEnd }.sumOf { it.expenseDelta }
        val yearlyRemainingExpense = realLine.days.filter { it.date in today..yearEnd }.sumOf { it.expenseDelta }

        return BudgetDashboardUiState.Loaded(
            budget = budget,
            activePlannedItems = activePlannedItems,
            today = today,
            todayBalance = todayBalance,
            varianceVsReference = varianceVsReference,
            realLine = realLine,
            referenceLine = referenceLine,
            latestSnapshotLabel = latestSnapshot?.label,
            daysRemaining = daysRemaining,
            monthlyProjectedExpense = monthlyProjectedExpense,
            monthlyExpenseExceedsBalance = todayBalance < monthlyRemainingExpense,
            yearlyProjectedExpense = yearlyProjectedExpense,
            yearlyExpenseExceedsBalance = todayBalance < yearlyRemainingExpense,
            unconvertedTemplateItemCount = unconvertedTemplateItems,
            unconvertedExpenseCount = unconvertedExpenses,
        )
    }

    /**
     * Botón "Activar" del header (pantalla 6, solo visible si está
     * pausado). Re-llama a [load] después para refrescar `budget.status`
     * en el estado — a diferencia de `BudgetFormViewModel.activate()`, acá
     * conviene porque el header de esta pantalla depende de ese status
     * para mostrar/ocultar el botón.
     */
    fun activate() {
        val budgetId = (_uiState.value as? BudgetDashboardUiState.Loaded)?.budget?.id ?: return
        viewModelScope.launch {
            budgetRepository.activate(budgetId)
            load(budgetId)
        }
    }

    /** "Pausar" manual del header (agregado en el chat) — ver KDoc de `BudgetRepository.pause`. Mismo criterio de refresco que [activate]. */
    fun pause() {
        val budgetId = (_uiState.value as? BudgetDashboardUiState.Loaded)?.budget?.id ?: return
        viewModelScope.launch {
            budgetRepository.pause(budgetId)
            load(budgetId)
        }
    }
}

sealed interface BudgetDashboardUiState {
    data object Loading : BudgetDashboardUiState
    data object NotFound : BudgetDashboardUiState

    data class Loaded(
        val budget: Budget,
        val activePlannedItems: List<PlannedItem>,
        val today: LocalDate,
        val todayBalance: Long,
        /** Ver KDoc de la clase ("varianceVsReference") — positivo = ahorraste, negativo = excediste, null sin línea de referencia comparable. */
        val varianceVsReference: Long?,
        val realLine: ProjectionResult,
        /** `null` solo si `ensureInitialSnapshot` no pudo crear ninguno (ver excepción de tasas sin convertir). */
        val referenceLine: ProjectionResult?,
        val latestSnapshotLabel: String?,
        /** Días hasta `realLine.breakEvenDate` (0 si ya se agotó). `null` si no se agota en el horizonte analizado. */
        val daysRemaining: Int?,
        /** Gasto proyectado del mes/año calendario completo en curso — ver KDoc de la clase. */
        val monthlyProjectedExpense: Long,
        /** `true` si `todayBalance` no alcanza a cubrir lo que falta gastar en lo que resta del mes. */
        val monthlyExpenseExceedsBalance: Boolean,
        val yearlyProjectedExpense: Long,
        /** `true` si `todayBalance` no alcanza a cubrir lo que falta gastar en lo que resta del año. */
        val yearlyExpenseExceedsBalance: Boolean,
        val unconvertedTemplateItemCount: Int,
        val unconvertedExpenseCount: Int,
    ) : BudgetDashboardUiState
}
