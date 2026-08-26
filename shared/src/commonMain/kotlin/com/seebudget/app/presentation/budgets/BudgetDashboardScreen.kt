package com.seebudget.app.presentation.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.projection.ProjectionDay
import com.seebudget.app.domain.projection.ProjectionResult
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.BrutalCard
import com.seebudget.app.presentation.components.TwoLineChart
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.BudgetDashboardUiState
import com.seebudget.app.presentation.viewmodel.BudgetDashboardViewModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-09 — Presupuesto: Detalle/Dashboard (sección 8, pantalla 6): header
 * con estado + Activar/Pausar, card de saldo actual + fecha de quiebre +
 * días restantes, resumen de gasto proyectado del mes/año (marcado en
 * rojo si el saldo actual no alcanza para cubrir lo que resta del
 * período — ver KDoc del ViewModel), gráfico de dos líneas con selector
 * de período (semana/mes/año) y leyenda, resumen de ítems planificados
 * activos, y accesos a "Historial de proyecciones" (#8) y "Ajustes del
 * presupuesto" (#10 — `BudgetFormScreen` en modo editar, que ya tiene la
 * zona de peligro Completar/Eliminar y el botón "Crear nueva línea de
 * referencia desde hoy" — no se duplican acá).
 *
 * **Selector de período del gráfico** (notas de UI, Fase 6; corregido dos
 * veces en el chat — ver KDoc de `sliceForChart` para el detalle final):
 * "Semana"/"Mes"/"Año" acotan el TOTAL de días mostrados (pasado +
 * futuro combinados) alrededor de un "pivote" (por defecto hoy) — no
 * solo el horizonte futuro. Independientemente del período elegido, el
 * gráfico nunca muestra más de una semana después de
 * `realLine.breakEvenDate` (pedido explícito del usuario) — ambos
 * límites se combinan tomando el más cercano.
 *
 * **Anterior/Siguiente** (agregado en el chat): navega el pivote de a
 * `period.days` por vez para poder recorrer TODA la proyección simulada
 * (hasta `ProjectionEngine.DEFAULT_HORIZON_DAYS` u `endDate`), no solo la
 * ventana inicial alrededor de hoy — los botones se deshabilitan al
 * llegar a cualquiera de los dos límites (`realLine.days.first().date` /
 * el límite de `breakEven + 7 días` o el último día simulado, lo que
 * ocurra antes — mismo límite que ya existía, ahora también aplicado a
 * la navegación en vez de solo a la ventana inicial). Cambiar de período
 * reinicia el pivote a hoy (evita ventanas confusas al saltar entre
 * "Semana" navegado lejos y, por ejemplo, "Año").
 *
 * "Check-in de hoy" (Fase 7, RF-10): navega siempre a la fecha de HOY
 * (`Clock.System.todayIn`, ver `LoadedDashboard`) — la navegación a días
 * anteriores vive dentro de la propia pantalla de Check-in
 * (`CheckInScreen`), no acá.
 */
@Composable
fun BudgetDashboardScreen(
    budgetId: String,
    onOpenSettings: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenCheckIn: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BudgetDashboardViewModel = koinViewModel(),
) {
    LaunchedEffect(budgetId) { viewModel.load(budgetId) }
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.budget_dashboard_back_button)) }
        Spacer(Modifier.height(8.dp))

        when (val state = uiState) {
            BudgetDashboardUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            BudgetDashboardUiState.NotFound -> Text(
                stringResource(Res.string.budget_dashboard_not_found),
                style = MaterialTheme.typography.bodyLarge,
            )

            is BudgetDashboardUiState.Loaded -> LoadedDashboard(
                state = state,
                onActivate = viewModel::activate,
                onPause = viewModel::pause,
                onOpenSettings = { onOpenSettings(budgetId) },
                onOpenHistory = { onOpenHistory(budgetId) },
                onOpenCheckIn = { onOpenCheckIn(budgetId) },
            )
        }
    }
}

@Composable
private fun LoadedDashboard(
    state: BudgetDashboardUiState.Loaded,
    onActivate: () -> Unit,
    onPause: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCheckIn: () -> Unit,
) {
    val colors = LocalSeeBudgetColors.current
    val budget = state.budget
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.MONTH) }
    // Reinicia a hoy cuando cambia el período — ver KDoc de la clase ("Anterior/Siguiente").
    var chartPivot by remember(state.budget.id, selectedPeriod) { mutableStateOf(state.today) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(budget.name, style = MaterialTheme.typography.headlineMedium)
                Text(statusLabel(budget.status), style = MaterialTheme.typography.labelMedium)
            }
            if (budget.status == BudgetStatus.PAUSED) {
                BrutalButton(onClick = onActivate, backgroundColor = colors.accentPositive, contentColor = Color.White) {
                    Text(stringResource(Res.string.budget_dashboard_button_activate))
                }
            }
            if (budget.status == BudgetStatus.ACTIVE) {
                BrutalButton(onClick = onPause, backgroundColor = colors.surface) {
                    Text(stringResource(Res.string.budget_dashboard_button_pause))
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        BrutalCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(Res.string.budget_dashboard_current_balance_label), style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "${MoneyFormat.toDecimalString(state.todayBalance, budget.baseCurrency)} ${budget.baseCurrency}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                state.varianceVsReference?.let { variance ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = varianceLabel(variance, budget.baseCurrency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (variance >= 0) colors.accentPositive else colors.accentError,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(breakEvenLabel(null, state.realLine), style = MaterialTheme.typography.bodySmall)
                if (state.daysRemaining != null) {
                    Text(
                        text = stringResource(Res.string.budget_dashboard_days_remaining_label, state.daysRemaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentAction,
                    )
                }
                state.referenceLine?.let { reference ->
                    val referenceLabel = state.latestSnapshotLabel
                        ?: stringResource(Res.string.budget_dashboard_projection_original_fallback)
                    Text(
                        breakEvenLabel(referenceLabel, reference),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textAndBorder,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // IntrinsicSize.Max en el Row + fillMaxHeight() en cada card: para que
        // ambas compartan la altura de la mas alta (la que muestra el mensaje
        // de alerta) en vez de que cada una quede con su propia altura de
        // wrap-content, dejando un desnivel cuando solo una tiene ese texto.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
            SpendSummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.budget_dashboard_monthly_expense_label),
                amount = state.monthlyProjectedExpense,
                currency = budget.baseCurrency,
                isOverBalance = state.monthlyExpenseExceedsBalance,
            )
            SpendSummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.budget_dashboard_yearly_expense_label),
                amount = state.yearlyProjectedExpense,
                currency = budget.baseCurrency,
                isOverBalance = state.yearlyExpenseExceedsBalance,
            )
        }
        Spacer(Modifier.height(24.dp))

        Text(stringResource(Res.string.budget_dashboard_projection_section_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Legend(realColor = colors.accentAction, referenceColor = colors.accentPositive, showReference = state.referenceLine != null)
        Spacer(Modifier.height(8.dp))
        PeriodSelector(selected = selectedPeriod, onSelect = { selectedPeriod = it })
        Spacer(Modifier.height(8.dp))

        val chartSlice = remember(state.realLine, state.referenceLine, chartPivot, selectedPeriod) {
            sliceForChart(
                realDays = state.realLine.days,
                referenceDays = state.referenceLine?.days,
                pivot = chartPivot,
                period = selectedPeriod,
                breakEven = state.realLine.breakEvenDate,
            )
        }
        ChartWindowNavigator(
            slice = chartSlice,
            onPrevious = { chartPivot = chartPivot.minus(selectedPeriod.days, DateTimeUnit.DAY) },
            onNext = { chartPivot = chartPivot.plus(selectedPeriod.days, DateTimeUnit.DAY) },
        )
        Spacer(Modifier.height(8.dp))
        // Envuelto en BrutalCard (decisión del chat): sin padding horizontal
        // para no achicar el ancho del gráfico, solo padding vertical.
        BrutalCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                TwoLineChart(
                    labels = chartSlice.realDays.map { dayLabel(it.date) },
                    realValues = chartSlice.realDays.map { minorUnitsToFloat(it.balance, budget.baseCurrency) },
                    referenceValues = chartSlice.referenceDays?.map { minorUnitsToFloat(it.balance, budget.baseCurrency) },
                    realColor = colors.accentAction,
                    referenceColor = colors.accentPositive,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        if (state.unconvertedTemplateItemCount > 0 || state.unconvertedExpenseCount > 0) {
            Text(
                text = stringResource(
                    Res.string.budget_dashboard_unconverted_warning,
                    budget.baseCurrency,
                    state.unconvertedTemplateItemCount,
                    state.unconvertedExpenseCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentError,
            )
            Spacer(Modifier.height(16.dp))
        }

        Text(stringResource(Res.string.budget_dashboard_planned_items_section_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (state.activePlannedItems.isEmpty()) {
            Text(stringResource(Res.string.budget_dashboard_no_planned_items_message), style = MaterialTheme.typography.bodySmall)
        } else {
            state.activePlannedItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .background(colors.surface)
                        .fillMaxWidth()
                        .border(BorderStroke(2.dp, colors.textAndBorder))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${typeLabel(item.type)} · ${frequencyLabel(item.frequency)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        "${MoneyFormat.toDecimalString(item.amount, item.currency)} ${item.currency}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))

        // Fase 7 (RF-10) — ver KDoc de la clase: siempre abre en la fecha
        // de hoy; navegar a días anteriores es una acción de CheckInScreen.
        BrutalButton(onClick = onOpenCheckIn, backgroundColor = colors.accentAction) {
            Text(stringResource(Res.string.budget_dashboard_button_checkin_today))
        }
        Spacer(Modifier.height(12.dp))
        BrutalButton(onClick = onOpenHistory, backgroundColor = colors.surface) {
            Text(stringResource(Res.string.budget_dashboard_button_history))
        }
        Spacer(Modifier.height(12.dp))
        BrutalButton(onClick = onOpenSettings, backgroundColor = colors.surface) {
            Text(stringResource(Res.string.budget_dashboard_button_settings))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SpendSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Long,
    currency: String,
    isOverBalance: Boolean,
) {
    val colors = LocalSeeBudgetColors.current
    BrutalCard(modifier = modifier, backgroundColor = colors.surface) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${MoneyFormat.toDecimalString(amount, currency)} $currency",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isOverBalance) colors.accentError else colors.textAndBorder,
            )
            if (isOverBalance) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.budget_dashboard_balance_insufficient_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentError,
                )
            }
        }
    }
}

/** Semana/mes/año del selector — ver KDoc de `BudgetDashboardScreen` sobre cómo se combina con el recorte por quiebre de saldo. */
private enum class ChartPeriod(val days: Int) {
    WEEK(7),
    MONTH(30),
    YEAR(365),
}

/** RNF-07 (selector de idioma, agregado en el chat): el label ya no vive en el enum (no puede llamar a `stringResource`, no es @Composable), se resuelve acá. */
@Composable
private fun periodLabel(period: ChartPeriod): String = when (period) {
    ChartPeriod.WEEK -> stringResource(Res.string.budget_dashboard_period_week)
    ChartPeriod.MONTH -> stringResource(Res.string.budget_dashboard_period_month)
    ChartPeriod.YEAR -> stringResource(Res.string.budget_dashboard_period_year)
}

@Composable
private fun PeriodSelector(selected: ChartPeriod, onSelect: (ChartPeriod) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartPeriod.entries.forEach { period ->
            val isSelected = period == selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(periodLabel(period), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Resultado de [sliceForChart]: las dos líneas ya recortadas a la
 * ventana visible, sus límites (para el label del [ChartWindowNavigator])
 * y si hay margen para seguir navegando en cada dirección (para
 * habilitar/deshabilitar "Anterior"/"Siguiente").
 */
private data class ChartSlice(
    val realDays: List<ProjectionDay>,
    val referenceDays: List<ProjectionDay>?,
    val windowStart: LocalDate?,
    val windowEnd: LocalDate?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

/**
 * Recorta ambas líneas a una ventana de `period.days` días alrededor de
 * [pivot] (por defecto hoy — ver `chartPivot` en `LoadedDashboard`).
 *
 * **Historia (tres correcciones sucesivas, todas en el chat):**
 * 1. Arrancaba en `max(primer día simulado, today)`, así que nunca se
 *    veían los días pasados — al ingresar una irregularidad en el
 *    Check-in, la línea real divergía de la referencia, pero como el
 *    gráfico solo mostraba desde hoy en adelante, no se veía el quiebre
 *    que lo explicaba (ver KDoc de `ProjectionEngine`/
 *    `BudgetDashboardViewModel` — las dos líneas siempre estuvieron bien
 *    calculadas, era un problema de ventana visible, no de cálculo).
 * 2. La corrección de #1 pasó a arrancar SIEMPRE en el primer día
 *    simulado (todo el historial visible) y dejó que `period.days`
 *    acotara solo el horizonte futuro — pero eso significaba que
 *    "Semana" podía mostrar bastante más de 7 días si el presupuesto ya
 *    llevaba corriendo un tiempo. Reportado como bug.
 * 3. Ahora `period.days` acota el TOTAL de días mostrados (pasado +
 *    futuro combinados) alrededor de [pivot], y se agregó navegación
 *    Anterior/Siguiente (mover [pivot]) para poder recorrer toda la
 *    proyección en vez de solo la ventana inicial.
 *
 * `windowEnd` se calcula como `pivot + period.days - 1`, recortado por
 * `breakEven + 7 días` (pedido explícito del usuario, ver KDoc de la
 * clase) y por el último día simulado — el más chico de los tres.
 * `windowStart` se calcula hacia atrás desde `windowEnd` para que el
 * total sea `period.days`, pero nunca antes del primer día simulado — así
 * un `endDate`/`breakEven` cercano no "desperdicia" días del período
 * mostrando de menos. `canGoForward` compara contra el mismo límite
 * `breakEven + 7 días`/último día simulado — Siguiente se deshabilita ahí
 * en vez de seguir avanzando sin mostrar nada nuevo.
 */
private fun sliceForChart(
    realDays: List<ProjectionDay>,
    referenceDays: List<ProjectionDay>?,
    pivot: LocalDate,
    period: ChartPeriod,
    breakEven: LocalDate?,
): ChartSlice {
    if (realDays.isEmpty()) return ChartSlice(realDays, referenceDays, null, null, canGoBack = false, canGoForward = false)
    val overallStart = realDays.first().date
    val overallEnd = realDays.last().date
    val capEnd = breakEven?.plus(7, DateTimeUnit.DAY)
    val maxWindowEnd = listOfNotNull(capEnd, overallEnd).min()

    val futureAnchor = maxOf(overallStart, pivot)
    val windowEnd = minOf(futureAnchor.plus(period.days - 1, DateTimeUnit.DAY), maxWindowEnd)
    val windowStart = maxOf(overallStart, windowEnd.minus(period.days - 1, DateTimeUnit.DAY))

    val slicedReal = realDays.filter { it.date in windowStart..windowEnd }
    val slicedReference = referenceDays?.filter { it.date in windowStart..windowEnd }
    return ChartSlice(
        realDays = slicedReal,
        referenceDays = slicedReference,
        windowStart = windowStart,
        windowEnd = windowEnd,
        canGoBack = windowStart > overallStart,
        canGoForward = windowEnd < maxWindowEnd,
    )
}

/** "‹ Anterior" / rango visible / "Siguiente ›" — ver KDoc de la clase ("Anterior/Siguiente") y de [sliceForChart]. */
@Composable
private fun ChartWindowNavigator(slice: ChartSlice, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = slice.canGoBack) { Text("‹") }
        val start = slice.windowStart
        val end = slice.windowEnd
        if (start != null && end != null) {
            Text(
                text = if (start == end) dayLabelFull(start) else "${dayLabel(start)} – ${dayLabelFull(end)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        TextButton(onClick = onNext, enabled = slice.canGoForward) { Text("›") }
    }
}

@Composable
private fun Legend(realColor: Color, referenceColor: Color, showReference: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(realColor, stringResource(Res.string.budget_dashboard_legend_real))
        if (showReference) LegendDot(referenceColor, stringResource(Res.string.budget_dashboard_legend_reference))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun statusLabel(status: BudgetStatus): String = when (status) {
    BudgetStatus.ACTIVE -> stringResource(Res.string.budget_dashboard_status_active)
    BudgetStatus.PAUSED -> stringResource(Res.string.budget_dashboard_status_paused)
    BudgetStatus.COMPLETED -> stringResource(Res.string.budget_dashboard_status_completed)
}

@Composable
private fun typeLabel(type: PlannedItemType): String = when (type) {
    PlannedItemType.INCOME -> stringResource(Res.string.budget_dashboard_type_income)
    PlannedItemType.EXPENSE -> stringResource(Res.string.budget_dashboard_type_expense)
}

@Composable
private fun frequencyLabel(frequency: PlannedItemFrequency): String = when (frequency) {
    PlannedItemFrequency.ONCE -> stringResource(Res.string.budget_dashboard_frequency_once)
    PlannedItemFrequency.DAILY -> stringResource(Res.string.budget_dashboard_frequency_daily)
    PlannedItemFrequency.WEEKLY -> stringResource(Res.string.budget_dashboard_frequency_weekly)
    PlannedItemFrequency.WEEKDAYS -> stringResource(Res.string.budget_dashboard_frequency_weekdays)
    PlannedItemFrequency.MONTHLY -> stringResource(Res.string.budget_dashboard_frequency_monthly)
}

/**
 * "Has ahorrado"/"Has excedido por" (agregado en el chat): compara el
 * saldo real de hoy contra el saldo de hoy en la línea de referencia —
 * ver KDoc de `BudgetDashboardViewModel.varianceVsReference`.
 */
@Composable
private fun varianceLabel(varianceMinorUnits: Long, currency: String): String {
    val amountText = MoneyFormat.toDecimalString(kotlin.math.abs(varianceMinorUnits), currency)
    return if (varianceMinorUnits >= 0) {
        stringResource(Res.string.budget_dashboard_variance_saved, amountText, currency)
    } else {
        stringResource(Res.string.budget_dashboard_variance_exceeded, amountText, currency)
    }
}

/**
 * `referenceLabel == null`: es la línea REAL ("Proyección actual"). No
 * nulo: es una línea de referencia congelada, ya con su label resuelto
 * (incluido el fallback "original" — ver KDoc de `LoadedDashboard`).
 */
@Composable
private fun breakEvenLabel(referenceLabel: String?, result: ProjectionResult): String {
    val breakEven = result.breakEvenDate
    return if (referenceLabel == null) {
        if (breakEven == null) {
            stringResource(Res.string.budget_dashboard_current_projection_no_breakeven)
        } else {
            stringResource(Res.string.budget_dashboard_current_projection_breakeven, dayLabelFull(breakEven))
        }
    } else {
        if (breakEven == null) {
            stringResource(Res.string.budget_dashboard_other_projection_no_breakeven, referenceLabel)
        } else {
            stringResource(Res.string.budget_dashboard_other_projection_breakeven, referenceLabel, dayLabelFull(breakEven))
        }
    }
}

/** Formato compacto (d/M) — solo para las etiquetas del eje X del gráfico, donde el formato largo no entra. */
private fun dayLabel(date: LocalDate): String = "${date.dayOfMonth}/${date.monthNumber}"

/** Formato largo ("1 de octubre de 2026" / "October 1, 2026") — para texto legible (fecha de quiebre, etc.). RNF-07: mes y orden salen de `strings.xml`. */
@Composable
private fun dayLabelFull(date: LocalDate): String =
    stringResource(Res.string.budget_dashboard_date_full_format, date.dayOfMonth, monthName(date.monthNumber), date.year)

@Composable
private fun monthName(monthNumber: Int): String = when (monthNumber) {
    1 -> stringResource(Res.string.budget_dashboard_month_1)
    2 -> stringResource(Res.string.budget_dashboard_month_2)
    3 -> stringResource(Res.string.budget_dashboard_month_3)
    4 -> stringResource(Res.string.budget_dashboard_month_4)
    5 -> stringResource(Res.string.budget_dashboard_month_5)
    6 -> stringResource(Res.string.budget_dashboard_month_6)
    7 -> stringResource(Res.string.budget_dashboard_month_7)
    8 -> stringResource(Res.string.budget_dashboard_month_8)
    9 -> stringResource(Res.string.budget_dashboard_month_9)
    10 -> stringResource(Res.string.budget_dashboard_month_10)
    11 -> stringResource(Res.string.budget_dashboard_month_11)
    else -> stringResource(Res.string.budget_dashboard_month_12)
}

/** Unidades menores -> mayores (float, solo para plotear el eje Y del gráfico) — ver mismo helper en ReportsScreen.kt. */
private fun minorUnitsToFloat(minorUnits: Long, currency: String): Float {
    val digits = MoneyFormat.minorUnitDigits(currency)
    var divisor = 1.0
    repeat(digits) { divisor *= 10.0 }
    return (minorUnits / divisor).toFloat()
}
