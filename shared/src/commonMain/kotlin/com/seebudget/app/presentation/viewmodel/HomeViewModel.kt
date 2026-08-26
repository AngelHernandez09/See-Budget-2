package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.projection.CheckInDayBuilder
import com.seebudget.app.domain.projection.ProjectionDay
import com.seebudget.app.domain.projection.ProjectionEngine
import com.seebudget.app.domain.projection.ProjectionTemplateItem
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.repository.ExchangeRateRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.repository.PlannedItemRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Pantalla 1 — Inicio (sección 8.4 #1 del documento), agregada en el chat
 * junto con la reorganización de secciones (Categorías pasa a vivir
 * dentro de Ajustes generales, ver `SettingsScreen`, y la bottom nav pasa
 * a las 5 secciones documentadas: Inicio·Gastos·Reportes·Presupuestos·
 * Ajustes — hasta ahora "Gastos" hacía de pantalla de arranque, ver nota
 * en App.kt sobre el gap de navegación arrastrado desde Fase 1).
 *
 * Compone piezas YA existentes en vez de reimplementar lógica: el
 * "saldo actual/fecha de quiebre proyectada" reusa `ProjectionEngine` con
 * el mismo criterio de conversión que `BudgetDashboardViewModel` (tasa de
 * HOY para la plantilla, tasa de la fecha de cada gasto real — ver KDoc
 * de esa clase); el contador de "Check-in de hoy" reusa `CheckInDayBuilder`
 * (misma regla de ocurrencia que Check-in/Dashboard, nunca diverge).
 *
 * **Decisiones de UI tomadas en el chat para esta pantalla** (el
 * documento las deja abiertas):
 * - "Total gastado hoy y/o en el mes": se interpreta literal ("gastado",
 *   no neto) — solo egresos (`PlannedItemType.EXPENSE`), a diferencia del
 *   desglose ingreso/egreso/neto que sí muestra el Listado de gastos
 *   completo (`ExpenseListViewModel.ExpenseTotal`). Acá es un vistazo
 *   rápido, no un reemplazo de esa pantalla.
 * - "Mini-gráfico de proyección": una sola línea (la real/vigente, no la
 *   de referencia), acotada a los próximos 30 días desde hoy — no
 *   replica el gráfico de dos líneas del Dashboard completo, que es a
 *   donde se llega al tocarlo.
 * - "Acceso a notificaciones" del header: no existe ninguna pantalla de
 *   notificaciones en el documento — el ícono del header navega directo
 *   a Ajustes generales (que ya tiene la sección Notificaciones).
 * - Banner "sugerí crear un presupuesto" (sin presupuesto activo):
 *   NO se implementa en esta pasada (decisión explícita del usuario en
 *   el chat) — sin presupuesto activo, la sección de presupuesto de esta
 *   pantalla simplemente no se muestra.
 *
 * **Montos sin convertir:** a diferencia de `ExpenseListViewModel`/
 * `BudgetDashboardViewModel`, acá NO se expone un contador de "N montos
 * no convertidos" — es un resumen rápido, no la fuente de verdad de
 * ningún total; un gasto que no se pudo convertir (sin caché y sin
 * conexión) simplemente no suma a `todayExpenseTotal`/`monthExpenseTotal`
 * ni a la proyección del presupuesto, mismo criterio de "no inventar una
 * conversión 1:1" que el resto de la app, solo que sin superficie de UI
 * dedicada acá.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val plannedItemRepository: PlannedItemRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val monthStart = LocalDate(today.year, today.monthNumber, 1)

    /** Ver KDoc de `ExpenseListViewModel.observeBaseCurrency` — mismo criterio. */
    private fun observeBaseCurrency(): Flow<String> {
        val userId = authRepository.currentUserId() ?: return flowOf("USD")
        return userSettingsRepository.observe(userId).map { it?.baseCurrency ?: "USD" }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        expenseRepository.observeAll(),
        categoryRepository.observeAll(),
        observeBaseCurrency(),
        budgetRepository.observeActive(),
    ) { expenses, categories, baseCurrency, activeBudget ->
        HomeCombinedInput(expenses, categories, baseCurrency, activeBudget)
    }.flatMapLatest { input -> budgetContextFlow(input) }
        .mapLatest { (input, budgetContext) -> buildState(input, budgetContext) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /**
     * `null` sin presupuesto ACTIVE, [BudgetContext] si lo hay — tipo de
     * retorno declarado a propósito (`Flow<Pair<HomeCombinedInput,
     * BudgetContext?>>`) para que las dos ramas (con/sin presupuesto)
     * infieran el mismo tipo sin ambigüedad, en vez de dejarlo a la
     * inferencia de un `if`/`else` inline dentro de `flatMapLatest`.
     */
    private fun budgetContextFlow(input: HomeCombinedInput): Flow<Pair<HomeCombinedInput, BudgetContext?>> {
        val budget = input.activeBudget
        if (budget == null) {
            val pair: Pair<HomeCombinedInput, BudgetContext?> = input to null
            return flowOf(pair)
        }
        return combine(
            plannedItemRepository.observeByBudget(budget.id),
            expenseRepository.observeByDateRange(budget.startDate, today),
        ) { plannedItems, rangeExpenses ->
            val context: BudgetContext = BudgetContext(
                activeItems = plannedItems.filter { it.isActive },
                budgetExpenses = rangeExpenses.filter { it.budgetId == budget.id },
            )
            val pair: Pair<HomeCombinedInput, BudgetContext?> = input to context
            pair
        }
    }

    private suspend fun buildState(input: HomeCombinedInput, budgetContext: BudgetContext?): HomeUiState.Loaded {
        val categoriesById = input.categories.associateBy { it.id }

        val todayExpenseTotal = sumExpenses(input.expenses.filter { it.date == today }, input.baseCurrency)
        val monthExpenseTotal = sumExpenses(
            input.expenses.filter { it.date >= monthStart && it.date <= today },
            input.baseCurrency,
        )
        val recentExpenses = input.expenses.sortedByDescending { it.date }.take(5)

        val budget = input.activeBudget
        val budgetSection = if (budget != null && budgetContext != null) {
            buildBudgetSection(budget, budgetContext)
        } else {
            null
        }

        return HomeUiState.Loaded(
            today = today,
            baseCurrency = input.baseCurrency,
            todayExpenseTotal = todayExpenseTotal,
            monthExpenseTotal = monthExpenseTotal,
            recentExpenses = recentExpenses,
            categoriesById = categoriesById,
            budgetSection = budgetSection,
        )
    }

    /** Suma solo egresos (`EXPENSE`) convertidos a [baseCurrency] — ver KDoc de la clase sobre por qué no es neto acá. */
    private suspend fun sumExpenses(expenses: List<Expense>, baseCurrency: String): Long {
        var total = 0L
        for (expense in expenses) {
            if (expense.type != PlannedItemType.EXPENSE) continue
            val rate = exchangeRateRepository.getRate(expense.date, expense.currency, baseCurrency) ?: continue
            total += MoneyFormat.convert(expense.amount, expense.currency, baseCurrency, rate)
        }
        return total
    }

    private suspend fun buildBudgetSection(budget: Budget, context: BudgetContext): HomeBudgetSection {
        val liveTemplate = context.activeItems.mapNotNull { item ->
            val rate = exchangeRateRepository.getRate(today, item.currency, budget.baseCurrency) ?: return@mapNotNull null
            ProjectionTemplateItem(
                plannedItemId = item.id,
                type = item.type,
                amount = MoneyFormat.convert(item.amount, item.currency, budget.baseCurrency, rate),
                frequency = item.frequency,
                billingDay = item.billingDay,
                specificDate = item.specificDate,
                dayOfWeek = item.dayOfWeek,
            )
        }

        val actualDeltasByDate = HashMap<LocalDate, Long>()
        for (expense in context.budgetExpenses) {
            val rate = exchangeRateRepository.getRate(expense.date, expense.currency, budget.baseCurrency) ?: continue
            val converted = MoneyFormat.convert(expense.amount, expense.currency, budget.baseCurrency, rate)
            val signed = when (expense.type) {
                PlannedItemType.INCOME -> converted
                PlannedItemType.EXPENSE -> -converted
            }
            actualDeltasByDate[expense.date] = (actualDeltasByDate[expense.date] ?: 0L) + signed
        }

        val projection = ProjectionEngine.project(
            startDate = budget.startDate,
            endDate = budget.endDate,
            initialBalance = budget.initialBalance,
            template = liveTemplate,
            today = today,
            actualDeltasByDate = actualDeltasByDate,
        )

        val todayBalance = projection.days.firstOrNull { it.date == today }?.balance ?: budget.initialBalance

        // Mini-gráfico (ver KDoc de la clase): solo los próximos 30 días
        // desde hoy, no el horizonte completo que usa el Dashboard.
        val miniChartWindow = projection.days.filter { it.date >= today }.take(30)

        val checkInDay = CheckInDayBuilder.build(
            date = today,
            activePlannedItems = context.activeItems,
            expensesForDay = context.budgetExpenses.filter { it.date == today },
        )

        return HomeBudgetSection(
            budget = budget,
            todayBalance = todayBalance,
            breakEvenDate = projection.breakEvenDate,
            miniChartWindow = miniChartWindow,
            checkInConfirmedCount = checkInDay.items.count { it.isConfirmed },
            checkInTotalCount = checkInDay.items.size,
        )
    }
}

private data class HomeCombinedInput(
    val expenses: List<Expense>,
    val categories: List<Category>,
    val baseCurrency: String,
    val activeBudget: Budget?,
)

private data class BudgetContext(
    val activeItems: List<PlannedItem>,
    val budgetExpenses: List<Expense>,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Loaded(
        val today: LocalDate,
        val baseCurrency: String,
        val todayExpenseTotal: Long,
        val monthExpenseTotal: Long,
        /** Últimos 5 gastos (cualquier moneda/tipo), más reciente primero — "link a Listado completo" en la UI. */
        val recentExpenses: List<Expense>,
        val categoriesById: Map<String, Category>,
        /** `null` = no hay presupuesto ACTIVE — la sección de presupuesto de Inicio no se muestra. */
        val budgetSection: HomeBudgetSection?,
    ) : HomeUiState
}

/** Ver KDoc de [HomeViewModel] sobre el recorte a 30 días de [miniChartWindow] y por qué es una sola línea. */
data class HomeBudgetSection(
    val budget: Budget,
    val todayBalance: Long,
    val breakEvenDate: LocalDate?,
    val miniChartWindow: List<ProjectionDay>,
    val checkInConfirmedCount: Int,
    val checkInTotalCount: Int,
)
