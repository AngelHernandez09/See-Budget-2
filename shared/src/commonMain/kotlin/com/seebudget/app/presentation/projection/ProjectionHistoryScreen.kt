package com.seebudget.app.presentation.projection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.projection.ItemChange
import com.seebudget.app.domain.projection.ProjectionDay
import com.seebudget.app.domain.projection.ProjectionResult
import com.seebudget.app.domain.projection.ProjectionSnapshotDiff
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalCard
import com.seebudget.app.presentation.components.ChartLine
import com.seebudget.app.presentation.components.MultiLineChart
import com.seebudget.app.presentation.components.TrendLineChart
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.HistoryEntry
import com.seebudget.app.presentation.viewmodel.ProjectionHistoryUiState
import com.seebudget.app.presentation.viewmodel.ProjectionHistoryViewModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-09 — "Historial de proyecciones" (documento, sección 8.4 #8): lista
 * de líneas de referencia (`ProjectionSnapshot`) de un presupuesto, más
 * reciente primero, cada una con su mini-preview de proyección y — al
 * tocarla — el detalle de qué cambió respecto a la línea anterior (ver
 * KDoc de `ProjectionHistoryViewModel`/`ProjectionSnapshotComparer`).
 * Cada card también tiene un botón "Eliminar" (con confirmación) para
 * borrar esa línea puntual — agregado en el chat, no estaba en el
 * documento original (ver KDoc de `ProjectionSnapshotRepository`).
 *
 * "Gráfico combinado" superponiendo todas las líneas (documento: lo
 * marca como "opcional") — implementado a pedido explícito del usuario
 * en el chat (`CombinedChartSection`, con `MultiLineChart` — ver KDoc de
 * ese componente sobre por qué necesita su propio eje X en vez de
 * reusar `TrendLineChart`/`TwoLineChart`). Solo se muestra si hay 2 o
 * más líneas — con una sola no aporta nada sobre su propia mini-preview.
 */
@Composable
fun ProjectionHistoryScreen(
    budgetId: String,
    onBack: () -> Unit,
    viewModel: ProjectionHistoryViewModel = koinViewModel(),
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
        TextButton(onClick = onBack) { Text(stringResource(Res.string.projection_history_back_button)) }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.projection_history_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        when (val state = uiState) {
            ProjectionHistoryUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            ProjectionHistoryUiState.NotFound -> Text(
                stringResource(Res.string.projection_history_not_found),
                style = MaterialTheme.typography.bodyLarge,
            )

            is ProjectionHistoryUiState.Loaded -> LoadedHistory(
                entries = state.entries,
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
private fun LoadedHistory(entries: List<HistoryEntry>, onDelete: (String) -> Unit) {
    if (entries.isEmpty()) {
        Text(
            stringResource(Res.string.projection_history_empty),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    var selectedEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    var entryPendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (entries.size > 1) {
            CombinedChartSection(entries = entries)
            Spacer(Modifier.height(24.dp))
        }
        entries.forEach { entry ->
            HistoryEntryCard(
                entry = entry,
                onClick = { selectedEntry = entry },
                onDeleteClick = { entryPendingDelete = entry },
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    selectedEntry?.let { entry ->
        DiffDialog(
            label = entry.snapshot.label,
            diff = entry.diffFromPrevious,
            currency = entry.snapshot.frozenBaseCurrency,
            onDismiss = { selectedEntry = null },
        )
    }

    entryPendingDelete?.let { entry ->
        ConfirmDialog(
            title = stringResource(Res.string.projection_history_delete_confirm_title),
            message = stringResource(Res.string.projection_history_delete_confirm_message, entry.snapshot.label),
            confirmLabel = stringResource(Res.string.projection_history_delete_button),
            onConfirm = { onDelete(entry.snapshot.id); entryPendingDelete = null },
            onDismiss = { entryPendingDelete = null },
        )
    }
}

/**
 * "Gráfico combinado" (documento: "opcional", ver KDoc de la pantalla).
 * Cada línea usa su propia [previewDays] (mismo recorte que la
 * mini-preview de su card, para que ambos gráficos muestren
 * consistentemente el mismo rango) convertida a offset de días desde la
 * fecha más vieja entre todas las líneas — ver KDoc de `MultiLineChart`
 * sobre por qué hace falta ese offset en vez de un índice compartido.
 */
@Composable
private fun CombinedChartSection(entries: List<HistoryEntry>) {
    val colors = LocalSeeBudgetColors.current
    val palette = listOf(colors.accentPositive, colors.accentAction, colors.textAndBorder)
    val ascending = entries.sortedBy { it.snapshot.createdAt }
    val referenceDate = ascending.firstNotNullOfOrNull { previewDays(it.previewResult).firstOrNull()?.date }
        ?: return

    val lines = ascending.mapIndexedNotNull { index, entry ->
        val days = previewDays(entry.previewResult)
        if (days.isEmpty()) return@mapIndexedNotNull null
        val points = days.map { day ->
            referenceDate.daysUntil(day.date) to minorUnitsToFloat(day.balance, entry.snapshot.frozenBaseCurrency)
        }
        ChartLine(points = points, color = colorForIndex(index, palette))
    }
    if (lines.isEmpty()) return

    Text(stringResource(Res.string.projection_history_combined_chart_label), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    CombinedLegend(entries = ascending, palette = palette)
    Spacer(Modifier.height(8.dp))
    // Envuelto en BrutalCard (decisión del chat): sin padding horizontal
    // para no achicar el ancho del gráfico, solo padding vertical.
    BrutalCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            MultiLineChart(
                lines = lines,
                referenceDate = referenceDate,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombinedLegend(entries: List<HistoryEntry>, palette: List<Color>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorForIndex(index, palette)))
                Spacer(Modifier.width(6.dp))
                Text(entry.snapshot.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Rota la paleta de a 3 colores; si hay más de 3 líneas, repite el color bajando la opacidad para que se sigan distinguiendo. */
private fun colorForIndex(index: Int, palette: List<Color>): Color {
    val base = palette[index % palette.size]
    val cycle = index / palette.size
    return if (cycle == 0) base else base.copy(alpha = (1f - 0.25f * cycle).coerceAtLeast(0.35f))
}

@Composable
private fun HistoryEntryCard(entry: HistoryEntry, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    val snapshot = entry.snapshot

    BrutalCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, backgroundColor = colors.surface) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(snapshot.label, style = MaterialTheme.typography.bodyLarge)
                    Text(createdAtLabel(snapshot.createdAt), style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val diff = entry.diffFromPrevious
                    Text(
                        text = when {
                            entry.isOriginal -> stringResource(Res.string.projection_history_status_original)
                            diff != null && diff.hasChanges -> stringResource(Res.string.projection_history_status_view_changes)
                            else -> stringResource(Res.string.projection_history_status_no_changes)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentAction,
                    )
                    // La línea original (autogenerada al crear el presupuesto) no se
                    // puede borrar desde acá — ver KDoc de ProjectionHistoryViewModel.
                    if (!entry.isOriginal) {
                        TextButton(onClick = onDeleteClick) {
                            Text(stringResource(Res.string.projection_history_delete_button), style = MaterialTheme.typography.labelSmall, color = colors.accentError)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val chartEntries = previewDays(entry.previewResult).map { day ->
                dayLabel(day.date) to minorUnitsToFloat(day.balance, snapshot.frozenBaseCurrency)
            }
            TrendLineChart(
                entries = chartEntries,
                lineColor = colors.accentPositive,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSeeBudgetColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = colors.accentError) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.projection_history_cancel_button)) } },
    )
}

@Composable
private fun DiffDialog(label: String, diff: ProjectionSnapshotDiff?, currency: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.projection_history_diff_dialog_title, label)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    diff == null -> Text(
                        stringResource(Res.string.projection_history_diff_first_snapshot),
                    )
                    !diff.hasChanges -> Text(stringResource(Res.string.projection_history_diff_no_changes))
                    else -> {
                        diff.initialBalanceChange?.let {
                            DiffLine(
                                stringResource(
                                    Res.string.projection_history_diff_initial_balance,
                                    MoneyFormat.toDecimalString(it.previous, currency),
                                    MoneyFormat.toDecimalString(it.current, currency),
                                    currency,
                                ),
                            )
                        }
                        diff.startDateChange?.let {
                            DiffLine(
                                stringResource(
                                    Res.string.projection_history_diff_start_date,
                                    dayLabelFull(it.previous),
                                    dayLabelFull(it.current),
                                ),
                            )
                        }
                        diff.baseCurrencyChange?.let {
                            DiffLine(stringResource(Res.string.projection_history_diff_base_currency, it.previous, it.current))
                        }
                        diff.addedItems.forEach {
                            DiffLine(stringResource(Res.string.projection_history_diff_item_added, it.name))
                        }
                        diff.removedItems.forEach {
                            DiffLine(stringResource(Res.string.projection_history_diff_item_removed, it.name))
                        }
                        diff.changedItems.forEach { change ->
                            DiffLine(
                                stringResource(
                                    Res.string.projection_history_diff_item_changed,
                                    change.current.name,
                                    changeDescription(change),
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.projection_history_diff_close_button)) } },
    )
}

@Composable
private fun DiffLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun changeDescription(change: ItemChange): String {
    val parts = mutableListOf<String>()
    if (change.previous.amount != change.current.amount || change.previous.currency != change.current.currency) {
        parts += stringResource(
            Res.string.projection_history_diff_amount_change,
            MoneyFormat.toDecimalString(change.previous.amount, change.previous.currency),
            change.previous.currency,
            MoneyFormat.toDecimalString(change.current.amount, change.current.currency),
            change.current.currency,
        )
    }
    if (change.previous.isActive != change.current.isActive) {
        parts += if (change.current.isActive) {
            stringResource(Res.string.projection_history_status_reactivated)
        } else {
            stringResource(Res.string.projection_history_status_deactivated)
        }
    }
    if (change.previous.frequency != change.current.frequency) {
        parts += stringResource(
            Res.string.projection_history_diff_frequency_change,
            frequencyLabel(change.previous.frequency),
            frequencyLabel(change.current.frequency),
        )
    }
    return if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
}

@Composable
private fun frequencyLabel(frequency: PlannedItemFrequency): String = when (frequency) {
    PlannedItemFrequency.ONCE -> stringResource(Res.string.projection_history_frequency_once)
    PlannedItemFrequency.DAILY -> stringResource(Res.string.projection_history_frequency_daily)
    PlannedItemFrequency.WEEKLY -> stringResource(Res.string.projection_history_frequency_weekly)
    PlannedItemFrequency.WEEKDAYS -> stringResource(Res.string.projection_history_frequency_weekdays)
    PlannedItemFrequency.MONTHLY -> stringResource(Res.string.projection_history_frequency_monthly)
}

/**
 * Recorta la mini-preview del sparkline a una ventana razonable (no todo
 * el rango simulado, que puede llegar a un año) — mismo criterio de
 * "no más de una semana después del quiebre" que el gráfico del
 * Dashboard (ver `sliceForChart` en `BudgetDashboardScreen`), más un
 * techo fijo de ~90 días para que la mini-preview no quede demasiado
 * densa cuando no hay quiebre de saldo.
 */
private fun previewDays(result: ProjectionResult): List<ProjectionDay> {
    if (result.days.isEmpty()) return result.days
    val start = result.days.first().date
    val capByBreakEven = result.breakEvenDate?.plus(7, DateTimeUnit.DAY)
    val capByWindow = start.plus(89, DateTimeUnit.DAY)
    val end = listOfNotNull(capByBreakEven, capByWindow, result.days.last().date).min()
    return result.days.filter { it.date <= end }
}

/** Formato compacto (d/M) — mismo criterio que el eje X del gráfico del Dashboard. */
private fun dayLabel(date: LocalDate): String = "${date.dayOfMonth}/${date.monthNumber}"

/** Formato largo en español ("1 de octubre de 2026") — mismo criterio que en BudgetDashboardScreen. */
private fun dayLabelFull(date: LocalDate): String =
    "${date.dayOfMonth} de ${SPANISH_MONTHS_FULL[date.monthNumber - 1]} de ${date.year}"

private fun createdAtLabel(instant: Instant): String {
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "${dayLabelFull(dateTime.date)}, $hour:$minute"
}

private val SPANISH_MONTHS_FULL = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** Unidades menores -> mayores (float, solo para plotear el eje Y del gráfico) — mismo helper que en BudgetDashboardScreen/ReportsScreen. */
private fun minorUnitsToFloat(minorUnits: Long, currency: String): Float {
    val digits = MoneyFormat.minorUnitDigits(currency)
    var divisor = 1.0
    repeat(digits) { divisor *= 10.0 }
    return (minorUnits / divisor).toFloat()
}
