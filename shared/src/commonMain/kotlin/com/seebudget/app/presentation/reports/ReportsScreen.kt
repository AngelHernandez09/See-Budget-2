package com.seebudget.app.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.ReportPeriod
import com.seebudget.app.domain.model.ReportPeriodType
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalCard
import com.seebudget.app.presentation.components.CategoryBarChart
import com.seebudget.app.presentation.components.CategoryIconBadge
import com.seebudget.app.presentation.components.CurrencyField
import com.seebudget.app.presentation.components.TrendLineChart
import com.seebudget.app.presentation.components.parseHexColor
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.CategoryAmount
import com.seebudget.app.presentation.viewmodel.ReportsViewModel
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-03 — Reportes (sección 8.4 #4 del documento): selector de periodo
 * con navegación anterior/siguiente, selector de moneda de visualización
 * (elegida al generar, no persiste — ver ReportsViewModel), gráfico de
 * tendencia en el tiempo, gráfico de distribución por categoría (barras,
 * ver CategoryBarChart.kt) + lista resumen con monto y %.
 *
 * Exportar CSV/PDF (RF-06) no es parte de esta pantalla — es Fase 8.
 *
 * Selector Gastos/Ingresos (agregado en el chat junto con el selector
 * Ingreso/Egreso de "Registrar/Editar gasto"): vista separada, nunca
 * neteada — ver KDoc de ReportsViewModel.kind sobre por qué.
 */
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(stringResource(Res.string.reports_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        PeriodTypeSelector(selected = uiState.period.type, onSelect = viewModel::onPeriodTypeChange)
        Spacer(Modifier.height(12.dp))

        ReportKindSelector(selected = uiState.kind, onSelect = viewModel::onKindChange)
        Spacer(Modifier.height(12.dp))

        PeriodNavigator(
            period = uiState.period,
            onPrevious = viewModel::onPreviousPeriod,
            onNext = viewModel::onNextPeriod,
        )
        Spacer(Modifier.height(16.dp))

        CurrencyField(
            selectedCode = uiState.currency,
            onCurrencyChange = viewModel::onCurrencyChange,
            label = stringResource(Res.string.reports_currency_label),
        )
        Spacer(Modifier.height(24.dp))

        if (uiState.isLoading) {
            Text(stringResource(Res.string.reports_loading), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text(
            text = "${reportTotalLabel(uiState.kind)}: ${MoneyFormat.toDecimalString(uiState.total, uiState.currency)} ${uiState.currency}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (uiState.unconvertedCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = reportUnconvertedWarning(uiState.kind, uiState.unconvertedCount),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentError,
            )
        }
        Spacer(Modifier.height(24.dp))

        Text(stringResource(Res.string.reports_trend_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        val trend = uiState.trend
        if (trend != null) {
            // Envuelto en BrutalCard (decisión del chat): sin padding
            // horizontal para no achicar el ancho del gráfico, solo padding
            // vertical.
            BrutalCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    TrendLineChart(
                        entries = trend.map { it.label to minorUnitsToFloat(it.amountMinorUnits, uiState.currency) },
                        lineColor = colors.accentAction,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
        } else {
            // period.type == DAY: sin granularidad menor a un día en el
            // modelo de datos (Expense.date, sin hora) — ver ReportsViewModel.
            Text(
                stringResource(Res.string.reports_trend_day_not_applicable),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))

        Text(stringResource(Res.string.reports_category_breakdown_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (uiState.categoryBreakdown.isEmpty()) {
            Text(reportEmptyLabel(uiState.kind), style = MaterialTheme.typography.bodyMedium)
        } else {
            // Envuelto en BrutalCard (decisión del chat): sin padding
            // horizontal para no achicar el ancho del gráfico, solo padding
            // vertical.
            BrutalCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    CategoryBarChart(
                        entries = uiState.categoryBreakdown.map { entry ->
                            Triple(
                                entry.category?.name ?: stringResource(Res.string.reports_no_category_label),
                                minorUnitsToFloat(entry.amountMinorUnits, uiState.currency),
                                parseHexColor(entry.category?.color ?: "#141410") ?: colors.textAndBorder,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            uiState.categoryBreakdown.forEach { entry ->
                CategoryBreakdownRow(entry = entry, currency = uiState.currency)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PeriodTypeSelector(selected: ReportPeriodType, onSelect: (ReportPeriodType) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportPeriodType.entries.forEach { type ->
            val isSelected = type == selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onSelect(type) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(periodTypeLabel(type), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReportKindSelector(selected: PlannedItemType, onSelect: (PlannedItemType) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlannedItemType.entries.forEach { type ->
            val isSelected = type == selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onSelect(type) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(reportKindLabel(type), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PeriodNavigator(period: ReportPeriod, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious) { Text("‹") }
        Text(periodLabel(period), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onNext) { Text("›") }
    }
}

@Composable
private fun CategoryBreakdownRow(entry: CategoryAmount, currency: String) {
    val colors = LocalSeeBudgetColors.current
    Row(
        modifier = Modifier
            .background(colors.surface)
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(
                name = entry.category?.name ?: "?",
                colorHex = entry.category?.color ?: "#141410",
                size = 28.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(entry.category?.name ?: stringResource(Res.string.reports_no_category_label), style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${MoneyFormat.toDecimalString(entry.amountMinorUnits, currency)} $currency",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(entry.fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Unidades menores -> mayores (float, solo para plotear el eje Y del gráfico). */
private fun minorUnitsToFloat(minorUnits: Long, currency: String): Float {
    val digits = MoneyFormat.minorUnitDigits(currency)
    var divisor = 1.0
    repeat(digits) { divisor *= 10.0 }
    return (minorUnits / divisor).toFloat()
}

@Composable
private fun periodTypeLabel(type: ReportPeriodType): String = when (type) {
    ReportPeriodType.DAY -> stringResource(Res.string.reports_period_day)
    ReportPeriodType.WEEK -> stringResource(Res.string.reports_period_week)
    ReportPeriodType.MONTH -> stringResource(Res.string.reports_period_month)
    ReportPeriodType.YEAR -> stringResource(Res.string.reports_period_year)
}

@Composable
private fun reportKindLabel(kind: PlannedItemType): String = when (kind) {
    PlannedItemType.EXPENSE -> stringResource(Res.string.reports_kind_expense)
    PlannedItemType.INCOME -> stringResource(Res.string.reports_kind_income)
}

@Composable
private fun reportTotalLabel(kind: PlannedItemType): String = when (kind) {
    PlannedItemType.EXPENSE -> stringResource(Res.string.reports_total_expense_label)
    PlannedItemType.INCOME -> stringResource(Res.string.reports_total_income_label)
}

/** Mensaje de "N ítems en otra moneda no se pudieron convertir" — el sustantivo varía con `kind` (ver KDoc de ReportsViewModel.kind). */
@Composable
private fun reportUnconvertedWarning(kind: PlannedItemType, count: Int): String = when (kind) {
    PlannedItemType.EXPENSE -> stringResource(Res.string.reports_unconverted_warning_expense, count)
    PlannedItemType.INCOME -> stringResource(Res.string.reports_unconverted_warning_income, count)
}

@Composable
private fun reportEmptyLabel(kind: PlannedItemType): String = when (kind) {
    PlannedItemType.EXPENSE -> stringResource(Res.string.reports_empty_expense)
    PlannedItemType.INCOME -> stringResource(Res.string.reports_empty_income)
}

// TODO: reemplazar por formateo localizado real cuando exista User.locale
// wiring (ver TODO análogo en ExpenseListScreen.dateHeaderLabel) — por
// ahora, igual que el resto de la app, español hardcodeado.
private val SPANISH_MONTHS_FULL = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
private val SPANISH_MONTHS_ABBREV = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
)

private fun periodLabel(period: ReportPeriod): String {
    val start = period.range.start
    return when (period.type) {
        ReportPeriodType.DAY ->
            "${start.dayOfMonth} de ${SPANISH_MONTHS_FULL[start.monthNumber - 1]} de ${start.year}"
        ReportPeriodType.WEEK -> {
            val end = period.range.endInclusive
            val startLabel = if (start.monthNumber == end.monthNumber) {
                "${start.dayOfMonth}"
            } else {
                "${start.dayOfMonth} ${SPANISH_MONTHS_ABBREV[start.monthNumber - 1]}"
            }
            "$startLabel – ${end.dayOfMonth} de ${SPANISH_MONTHS_FULL[end.monthNumber - 1]} de ${end.year}"
        }
        ReportPeriodType.MONTH ->
            "${SPANISH_MONTHS_FULL[start.monthNumber - 1].replaceFirstChar { it.uppercase() }} ${start.year}"
        ReportPeriodType.YEAR -> "${start.year}"
    }
}
