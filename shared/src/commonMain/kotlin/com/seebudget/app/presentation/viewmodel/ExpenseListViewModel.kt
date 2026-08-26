package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.repository.ExchangeRateRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * RF-01 — Listado de gastos (sección 8.4 #3 del documento). Fase 1:
 * lista completa sin filtros de fecha todavía — observeByDateRange() del
 * repositorio queda listo para cuando exista una UI de filtros; no es
 * parte de este primer corte.
 *
 * Depende también de CategoryRepository (no solo ExpenseRepository) para
 * exponer `categoriesById` (y ahora `categories`, ver más abajo): la UI
 * necesita nombre/color/ícono de la categoría de cada gasto (por su
 * categoryId), y la lista completa para armar el panel de filtro por
 * categoría — no tiene sentido crear un segundo ViewModel solo para eso
 * en la misma pantalla.
 *
 * `total` (Fase 3): antes sumaba `expense.amount` crudo sin importar la
 * moneda de cada gasto — válido mientras todo gasto era forzosamente USD
 * (Fase 1), pero un bug real desde que existe selector de moneda
 * (ExpenseFormScreen). Ahora convierte cada gasto a la moneda base del
 * usuario (con la tasa de LA FECHA de ese gasto, mismo criterio que RF-03)
 * antes de sumar. Reactivo a la moneda base: si el usuario la cambia en
 * Ajustes, el total se recalcula solo.
 *
 * `total` (agregado en el chat, junto con el selector Ingreso/Egreso de
 * "Registrar/Editar gasto"): antes sumaba todo como egreso puro
 * (`Expense.type` solo lo fijaba el Check-in diario hasta ahora, ver
 * `ExpenseFormViewModel`) — deuda técnica ya anotada, ahora corregida:
 * [ExpenseTotal] separa ingresos y egresos, y expone el neto
 * (ingresos - egresos) — mismo criterio de signo que ya usa
 * `BudgetDashboardViewModel.actualDeltasByDate`.
 *
 * **Filtros/búsqueda/orden (agregado en el chat — sección 8.4 #3 del
 * documento, "Barra de filtros"/"Barra de búsqueda"):**
 * - Categoría: multi-select, tal como especifica el documento — un
 *   conjunto vacío significa "todas".
 * - Vínculo a presupuesto: tri-estado Todos/Con presupuesto/Sin
 *   presupuesto (el documento solo pedía "con/sin presupuesto"; se
 *   agregó "Todos" como estado neutro por defecto, ya que sin él el
 *   filtro no tendría un valor inicial que no excluyera nada).
 * - Monto: rango mínimo/máximo, siguiendo el mismo `AmountField` "estilo
 *   caja registradora" que el resto de la app — 0 en cualquiera de los
 *   dos extremos significa "sin límite" en ese extremo (decisión tomada
 *   en el chat: un monto real de 0 no es un caso de uso real acá, así
 *   que no hace falta un estado "sin definir" aparte).
 * - Búsqueda: solo por nota (`Expense.note`), separado del filtro de
 *   monto — el documento original describía una única "barra de
 *   búsqueda (nota/monto)"; se dividió en dos controles distintos a
 *   pedido explícito del usuario en el chat (más preciso que adivinar si
 *   un texto tipeado es una nota o un monto).
 * - Moneda: multi-select (mismo criterio que categoría) — pero sobre
 *   [usedCurrencies], las monedas que efectivamente aparecen en los
 *   gastos del usuario, no el catálogo completo de ~30 monedas de
 *   `CurrencyCatalog` (esa lista es para elegir la moneda de UN gasto al
 *   cargarlo, acá el objetivo es filtrar lo que ya existe).
 * - Rango de fechas: Desde/Hasta, ambos opcionales — mismo criterio que
 *   el filtro de monto (límites independientes, cualquiera de los dos
 *   puede quedar sin definir).
 * - Orden por monto (agregado en el chat, no estaba en el documento
 *   original): mayor a menor / menor a mayor. Reemplaza el agrupado por
 *   fecha por una lista plana (decisión tomada en el chat) — mezclar
 *   "ordenado por monto" con headers de fecha no tiene un orden natural
 *   único, así que la UI (`ExpenseListScreen`) muestra la lista agrupada
 *   SOLO cuando `sortOption == NONE`.
 * - El monto (filtro Y orden) se compara siempre en la moneda BASE del
 *   usuario, no en la moneda original de cada gasto — mismo criterio que
 *   ya usa `total` de esta clase para poder sumar gastos de distintas
 *   monedas; comparar montos crudos sin convertir mezclaría, por ejemplo,
 *   100 JPY con 100 USD como si fueran equivalentes. Gastos que no se
 *   pueden convertir (sin tasa cacheada y sin conexión) quedan afuera del
 *   resultado filtrado/ordenado mientras el filtro o el orden por monto
 *   estén activos — se cuentan en
 *   [FilteredExpensesResult.unconvertedForAmountCount] para que la UI lo
 *   avise, mismo criterio que [ExpenseTotal.unconvertedCount].
 * - `total` ahora se calcula sobre el resultado FILTRADO, no sobre la
 *   lista completa — así es como el documento ya lo describía ("Total
 *   acumulado del filtro activo").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseListViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val expenses: StateFlow<List<Expense>> = expenseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Para distinguir, en la UI, "todavia no hay ningun gasto" de "ningun
    // gasto coincide con los filtros activos" (`filtered.expenses` vacio
    // por si solo no alcanza para diferenciar esos dos casos).
    val hasAnyExpenses: StateFlow<Boolean> = expenses.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val categoriesById: StateFlow<Map<String, Category>> = categoryRepository.observeAll()
        .map { categories -> categories.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val baseCurrency: StateFlow<String> = observeBaseCurrency()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    // Ver KDoc de la clase, sección "Filtros/búsqueda/orden" — moneda.
    val usedCurrencies: StateFlow<List<String>> = expenses
        .map { list -> list.map { it.currency }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategoryIds: StateFlow<Set<String>> = _selectedCategoryIds.asStateFlow()

    // 0L = sin límite en ese extremo — ver KDoc de la clase.
    private val _minAmountFilter = MutableStateFlow(0L)
    val minAmountFilter: StateFlow<Long> = _minAmountFilter.asStateFlow()

    private val _maxAmountFilter = MutableStateFlow(0L)
    val maxAmountFilter: StateFlow<Long> = _maxAmountFilter.asStateFlow()

    private val _budgetLinkFilter = MutableStateFlow(BudgetLinkFilter.ALL)
    val budgetLinkFilter: StateFlow<BudgetLinkFilter> = _budgetLinkFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(ExpenseSortOption.NONE)
    val sortOption: StateFlow<ExpenseSortOption> = _sortOption.asStateFlow()

    private val _selectedCurrencies = MutableStateFlow<Set<String>>(emptySet())
    val selectedCurrencies: StateFlow<Set<String>> = _selectedCurrencies.asStateFlow()

    private val _dateFromFilter = MutableStateFlow<LocalDate?>(null)
    val dateFromFilter: StateFlow<LocalDate?> = _dateFromFilter.asStateFlow()

    private val _dateToFilter = MutableStateFlow<LocalDate?>(null)
    val dateToFilter: StateFlow<LocalDate?> = _dateToFilter.asStateFlow()

    /**
     * Combine de a 3 (tres veces) en vez de uno solo de 9 flows: el
     * overload de `combine` para más de 5 flows de tipos distintos pierde
     * el tipado por posición (queda un `Array<Any?>` para castear a
     * mano) — con tres combine anidados de a 3 el compilador infiere todo
     * sin casts, mismo criterio ya usado en `HomeViewModel`.
     */
    private data class CategoryAndAmountFilters(val categoryIds: Set<String>, val minAmount: Long, val maxAmount: Long)
    private data class LinkQueryAndSort(val budgetLink: BudgetLinkFilter, val query: String, val sort: ExpenseSortOption)
    private data class CurrencyAndDateFilters(val currencies: Set<String>, val dateFrom: LocalDate?, val dateTo: LocalDate?)
    private data class FilterState(
        val categoryIds: Set<String>,
        val minAmount: Long,
        val maxAmount: Long,
        val budgetLink: BudgetLinkFilter,
        val query: String,
        val sort: ExpenseSortOption,
        val currencies: Set<String>,
        val dateFrom: LocalDate?,
        val dateTo: LocalDate?,
    )

    private val filterStateFlow: Flow<FilterState> = combine(
        combine(_selectedCategoryIds, _minAmountFilter, _maxAmountFilter) { categoryIds, min, max ->
            CategoryAndAmountFilters(categoryIds, min, max)
        },
        combine(_budgetLinkFilter, _searchQuery, _sortOption) { budgetLink, query, sort ->
            LinkQueryAndSort(budgetLink, query, sort)
        },
        combine(_selectedCurrencies, _dateFromFilter, _dateToFilter) { currencies, dateFrom, dateTo ->
            CurrencyAndDateFilters(currencies, dateFrom, dateTo)
        },
    ) { categoryAndAmount, linkQueryAndSort, currencyAndDate ->
        FilterState(
            categoryIds = categoryAndAmount.categoryIds,
            minAmount = categoryAndAmount.minAmount,
            maxAmount = categoryAndAmount.maxAmount,
            budgetLink = linkQueryAndSort.budgetLink,
            query = linkQueryAndSort.query,
            sort = linkQueryAndSort.sort,
            currencies = currencyAndDate.currencies,
            dateFrom = currencyAndDate.dateFrom,
            dateTo = currencyAndDate.dateTo,
        )
    }

    private data class ExpensesFiltersAndCurrency(val list: List<Expense>, val filters: FilterState, val baseCurrency: String)

    val filtered: StateFlow<FilteredExpensesResult> = combine(expenses, filterStateFlow, baseCurrency) { list, filters, currency ->
        ExpensesFiltersAndCurrency(list, filters, currency)
    }.mapLatest { (list, filters, currency) -> applyFilters(list, filters, currency) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilteredExpensesResult(emptyList(), 0))

    val total: StateFlow<ExpenseTotal?> = combine(filtered, baseCurrency) { result, currency -> result.expenses to currency }
        .mapLatest { (list, baseCurrency) -> if (list.isEmpty()) null else computeTotal(list, baseCurrency) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun observeBaseCurrency(): Flow<String> {
        val userId = authRepository.currentUserId() ?: return flowOf("USD")
        return userSettingsRepository.observe(userId).map { it?.baseCurrency ?: "USD" }
    }

    fun toggleCategoryFilter(categoryId: String) {
        _selectedCategoryIds.update { current -> if (categoryId in current) current - categoryId else current + categoryId }
    }

    fun setAmountRangeFilter(minMinorUnits: Long, maxMinorUnits: Long) {
        _minAmountFilter.value = minMinorUnits
        _maxAmountFilter.value = maxMinorUnits
    }

    fun setBudgetLinkFilter(filter: BudgetLinkFilter) {
        _budgetLinkFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: ExpenseSortOption) {
        _sortOption.value = option
    }

    fun toggleCurrencyFilter(currencyCode: String) {
        _selectedCurrencies.update { current -> if (currencyCode in current) current - currencyCode else current + currencyCode }
    }

    fun setDateRangeFilter(from: LocalDate?, to: LocalDate?) {
        _dateFromFilter.value = from
        _dateToFilter.value = to
    }

    fun clearAllFilters() {
        _selectedCategoryIds.value = emptySet()
        _minAmountFilter.value = 0L
        _maxAmountFilter.value = 0L
        _budgetLinkFilter.value = BudgetLinkFilter.ALL
        _searchQuery.value = ""
        _sortOption.value = ExpenseSortOption.NONE
        _selectedCurrencies.value = emptySet()
        _dateFromFilter.value = null
        _dateToFilter.value = null
    }

    /**
     * Ver KDoc de la clase sobre el criterio de conversión de moneda para
     * el filtro/orden por monto. Solo hace las conversiones (una llamada
     * a `ExchangeRateRepository` por gasto) cuando realmente hacen falta
     * — filtro de monto activo (min u max > 0) u orden por monto activo
     * — para no pagar ese costo en el caso común de "sin filtro de
     * monto".
     */
    private suspend fun applyFilters(list: List<Expense>, filters: FilterState, baseCurrency: String): FilteredExpensesResult {
        var working = list

        if (filters.categoryIds.isNotEmpty()) {
            working = working.filter { it.categoryId in filters.categoryIds }
        }

        working = when (filters.budgetLink) {
            BudgetLinkFilter.ALL -> working
            BudgetLinkFilter.LINKED -> working.filter { it.budgetId != null }
            BudgetLinkFilter.UNLINKED -> working.filter { it.budgetId == null }
        }

        if (filters.query.isNotBlank()) {
            working = working.filter { it.note?.contains(filters.query, ignoreCase = true) == true }
        }

        if (filters.currencies.isNotEmpty()) {
            working = working.filter { it.currency in filters.currencies }
        }

        val dateFrom = filters.dateFrom
        if (dateFrom != null) {
            working = working.filter { it.date >= dateFrom }
        }
        val dateTo = filters.dateTo
        if (dateTo != null) {
            working = working.filter { it.date <= dateTo }
        }

        val needsAmountConversion = filters.minAmount > 0L || filters.maxAmount > 0L || filters.sort != ExpenseSortOption.NONE
        if (!needsAmountConversion) {
            return FilteredExpensesResult(expenses = working, unconvertedForAmountCount = 0)
        }

        val convertedById = HashMap<String, Long>()
        var unconvertedCount = 0
        for (expense in working) {
            val rate = exchangeRateRepository.getRate(expense.date, expense.currency, baseCurrency)
            if (rate == null) {
                unconvertedCount++
                continue
            }
            convertedById[expense.id] = MoneyFormat.convert(expense.amount, expense.currency, baseCurrency, rate)
        }
        // Sin tasa no hay forma de saber si el gasto entra en el rango ni
        // dónde ubicarlo en un orden por monto — se excluye del resultado
        // (contado en unconvertedForAmountCount) en vez de dejarlo afuera
        // de rango/orden en silencio.
        working = working.filter { convertedById.containsKey(it.id) }

        if (filters.minAmount > 0L) {
            working = working.filter { (convertedById[it.id] ?: 0L) >= filters.minAmount }
        }
        if (filters.maxAmount > 0L) {
            working = working.filter { (convertedById[it.id] ?: 0L) <= filters.maxAmount }
        }

        working = when (filters.sort) {
            ExpenseSortOption.AMOUNT_DESC -> working.sortedByDescending { convertedById[it.id] ?: 0L }
            ExpenseSortOption.AMOUNT_ASC -> working.sortedBy { convertedById[it.id] ?: 0L }
            ExpenseSortOption.NONE -> working
        }

        return FilteredExpensesResult(expenses = working, unconvertedForAmountCount = unconvertedCount)
    }

    /**
     * `unconvertedCount` > 0: algún gasto en otra moneda no se pudo
     * convertir (sin caché local y sin conexión para pedirla) — la UI lo
     * muestra en vez de fingir un total completo con esos gastos afuera
     * en silencio.
     */
    private suspend fun computeTotal(expenses: List<Expense>, baseCurrency: String): ExpenseTotal {
        var income = 0L
        var expense = 0L
        var unconverted = 0
        for (item in expenses) {
            val rate = exchangeRateRepository.getRate(item.date, item.currency, baseCurrency)
            if (rate == null) {
                unconverted++
                continue
            }
            val converted = MoneyFormat.convert(item.amount, item.currency, baseCurrency, rate)
            when (item.type) {
                PlannedItemType.INCOME -> income += converted
                PlannedItemType.EXPENSE -> expense += converted
            }
        }
        return ExpenseTotal(
            incomeMinorUnits = income,
            expenseMinorUnits = expense,
            netMinorUnits = income - expense,
            currency = baseCurrency,
            unconvertedCount = unconverted,
        )
    }

    fun delete(id: String) {
        viewModelScope.launch { expenseRepository.delete(id) }
    }
}

/** Ver KDoc de la clase sobre por qué separa ingresos/egresos en vez de un solo monto. */
data class ExpenseTotal(
    val incomeMinorUnits: Long,
    val expenseMinorUnits: Long,
    val netMinorUnits: Long,
    val currency: String,
    val unconvertedCount: Int,
)

/** Ver KDoc de la clase, sección "Filtros/búsqueda/orden". */
enum class BudgetLinkFilter { ALL, LINKED, UNLINKED }

/** Ver KDoc de la clase, sección "Filtros/búsqueda/orden". */
enum class ExpenseSortOption { NONE, AMOUNT_DESC, AMOUNT_ASC }

/** Ver KDoc de la clase, sección "Filtros/búsqueda/orden". */
data class FilteredExpensesResult(
    val expenses: List<Expense>,
    val unconvertedForAmountCount: Int,
)
