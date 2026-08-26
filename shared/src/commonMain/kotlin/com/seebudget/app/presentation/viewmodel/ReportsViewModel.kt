package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.ReportPeriod
import com.seebudget.app.domain.model.ReportPeriodType
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.repository.ExchangeRateRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * RF-03 — Reportes (sección 8.4 #4 del documento): selector de periodo con
 * navegación anterior/siguiente, selector de moneda de visualización
 * (elegida al generar, "no altera el dato original" — no se persiste en
 * ningún lado, a diferencia de `User.baseCurrency`), distribución por
 * categoría, y tendencia en el tiempo.
 *
 * Conversión: cada gasto se convierte a `displayCurrency` con la tasa de
 * SU PROPIA fecha (mismo criterio de RF-03/Fase 3 que ya usa
 * ExpenseListViewModel.total) — no con la tasa de hoy. `unconvertedCount`
 * en el resultado indica cuántos gastos no se pudieron convertir (sin
 * caché ni conexión); no se descartan en silencio.
 *
 * Tendencia (`trend`): null cuando `period.type == DAY` — no hay
 * granularidad menor a un día en el modelo de datos (`Expense.date`, sin
 * hora), así que un gráfico de "tendencia en el tiempo" no tiene sentido
 * dentro de un único día. Para WEEK/MONTH se bucketea por día; para YEAR,
 * por mes.
 *
 * `kind` (agregado en el chat junto con el selector Ingreso/Egreso de
 * "Registrar/Editar gasto"): vista de Gastos/Ingresos por separado, nunca
 * neteadas en un mismo reporte — a diferencia de ExpenseListViewModel.total
 * (que sí muestra el neto además de cada uno por separado), acá el pedido
 * explícito fue "otra vista para visualizar solo los ingresos". Por
 * defecto EXPENSE, mismo comportamiento que antes de que existiera este
 * selector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val _period = MutableStateFlow(ReportPeriod(ReportPeriodType.MONTH, today))
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    // Fallback igual que ExpenseFormViewModel: "USD" hasta que resuelva la
    // moneda base real (ver init) — evitable si el perfil local todavía no
    // existe (carrera con SyncTrigger.ensureProfile(), ver App.kt).
    private val _displayCurrency = MutableStateFlow("USD")
    val displayCurrency: StateFlow<String> = _displayCurrency.asStateFlow()

    private val _kind = MutableStateFlow(PlannedItemType.EXPENSE)
    val kind: StateFlow<PlannedItemType> = _kind.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.currentUserId() ?: return@launch
            val baseCurrency = userSettingsRepository.observe(userId).first()?.baseCurrency ?: return@launch
            _displayCurrency.value = baseCurrency
        }
    }

    val uiState: StateFlow<ReportUiState> =
        combine(_period, _displayCurrency, _kind) { period, currency, kind -> ReportFilters(period, currency, kind) }
            .flatMapLatest { filters ->
                combine(
                    expenseRepository.observeByDateRange(filters.period.range.start, filters.period.range.endInclusive),
                    categoryRepository.observeAll(),
                ) { expenses, categories ->
                    ReportInputs(filters.period, filters.currency, filters.kind, expenses.filter { it.type == filters.kind }, categories)
                }
            }
            .mapLatest { inputs -> buildReport(inputs) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ReportUiState.loading(_period.value, _displayCurrency.value, _kind.value),
            )

    fun onPeriodTypeChange(type: ReportPeriodType) {
        _period.update { ReportPeriod(type, it.referenceDate) }
    }

    fun onPreviousPeriod() {
        _period.update { it.previous() }
    }

    fun onNextPeriod() {
        _period.update { it.next() }
    }

    fun onCurrencyChange(code: String) {
        _displayCurrency.value = code
    }

    fun onKindChange(kind: PlannedItemType) {
        _kind.value = kind
    }

    private suspend fun buildReport(inputs: ReportInputs): ReportUiState {
        val (period, currency, _, expenses, categories) = inputs
        val categoriesById = categories.associateBy { it.id }

        var total = 0L
        var unconverted = 0
        val perCategory = LinkedHashMap<String, Long>()
        // Clave de bucket: LocalDate para WEEK/MONTH, número de meses (1..12) para YEAR.
        val perBucket = LinkedHashMap<Any, Long>()

        for (expense in expenses) {
            val rate = exchangeRateRepository.getRate(expense.date, expense.currency, currency)
            if (rate == null) {
                unconverted++
                continue
            }
            val converted = MoneyFormat.convert(expense.amount, expense.currency, currency, rate)
            total += converted
            perCategory[expense.categoryId] = (perCategory[expense.categoryId] ?: 0L) + converted
            if (period.type != ReportPeriodType.DAY) {
                val bucketKey: Any = if (period.type == ReportPeriodType.YEAR) expense.date.monthNumber else expense.date
                perBucket[bucketKey] = (perBucket[bucketKey] ?: 0L) + converted
            }
        }

        val categoryBreakdown = perCategory.entries
            .sortedByDescending { it.value }
            .map { (categoryId, amount) ->
                CategoryAmount(
                    category = categoriesById[categoryId],
                    amountMinorUnits = amount,
                    fraction = if (total == 0L) 0f else amount.toFloat() / total.toFloat(),
                )
            }

        val trend = when (period.type) {
            ReportPeriodType.DAY -> null
            ReportPeriodType.YEAR -> (1..12).map { month ->
                TrendPoint(label = SPANISH_MONTH_ABBREV[month - 1], amountMinorUnits = perBucket[month] ?: 0L)
            }
            ReportPeriodType.WEEK, ReportPeriodType.MONTH -> {
                val start = period.range.start
                val end = period.range.endInclusive
                generateSequence(start) { d -> if (d < end) d.plus(1, DateTimeUnit.DAY) else null }
                    .map { day -> TrendPoint(label = day.dayOfMonth.toString(), amountMinorUnits = perBucket[day] ?: 0L) }
                    .toList()
            }
        }

        return ReportUiState(
            period = period,
            currency = currency,
            kind = inputs.kind,
            total = total,
            categoryBreakdown = categoryBreakdown,
            trend = trend,
            unconvertedCount = unconverted,
            isLoading = false,
        )
    }

    private companion object {
        val SPANISH_MONTH_ABBREV = listOf(
            "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic",
        )
    }
}

private data class ReportFilters(
    val period: ReportPeriod,
    val currency: String,
    val kind: PlannedItemType,
)

private data class ReportInputs(
    val period: ReportPeriod,
    val currency: String,
    val kind: PlannedItemType,
    val expenses: List<Expense>,
    val categories: List<Category>,
)

data class ReportUiState(
    val period: ReportPeriod,
    val currency: String,
    val kind: PlannedItemType,
    val total: Long,
    val categoryBreakdown: List<CategoryAmount>,
    val trend: List<TrendPoint>?,
    val unconvertedCount: Int,
    val isLoading: Boolean,
) {
    companion object {
        fun loading(period: ReportPeriod, currency: String, kind: PlannedItemType) = ReportUiState(
            period = period,
            currency = currency,
            kind = kind,
            total = 0L,
            categoryBreakdown = emptyList(),
            trend = null,
            unconvertedCount = 0,
            isLoading = true,
        )
    }
}

data class CategoryAmount(val category: Category?, val amountMinorUnits: Long, val fraction: Float)

data class TrendPoint(val label: String, val amountMinorUnits: Long)
