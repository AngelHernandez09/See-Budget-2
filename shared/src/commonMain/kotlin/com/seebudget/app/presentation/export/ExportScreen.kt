package com.seebudget.app.presentation.export

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.export.ExportResult
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.ExportStatus
import com.seebudget.app.presentation.viewmodel.ExportViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-06 — Exportación (ver KDoc de `ExportViewModel`): selector de rango
 * de fechas + botones "Exportar a CSV"/"Exportar a PDF". Alcanzable
 * desde Ajustes.
 */
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = koinViewModel()) {
    val from by viewModel.from.collectAsState()
    val to by viewModel.to.collectAsState()
    val status by viewModel.status.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.export_back_button)) }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.export_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.export_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        Text(stringResource(Res.string.export_from_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        ExportDateField(date = from, onDateChange = viewModel::onFromChange)
        Spacer(Modifier.height(16.dp))

        Text(stringResource(Res.string.export_to_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        ExportDateField(date = to, onDateChange = viewModel::onToChange)
        Spacer(Modifier.height(24.dp))

        val isExporting = status == ExportStatus.Exporting
        BrutalButton(onClick = viewModel::onExportCsv, enabled = !isExporting) {
            Text(stringResource(Res.string.export_csv_button))
        }
        Spacer(Modifier.height(12.dp))
        BrutalButton(onClick = viewModel::onExportPdf, enabled = !isExporting) {
            Text(stringResource(Res.string.export_pdf_button))
        }
        Spacer(Modifier.height(16.dp))

        when (val current = status) {
            ExportStatus.Idle -> Unit
            ExportStatus.Exporting -> CircularProgressIndicator()
            ExportStatus.InvalidDateRange -> Text(
                stringResource(Res.string.export_invalid_date_range),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accentError,
            )
            is ExportStatus.Done -> ExportResultSection(result = current.result, onShare = viewModel::onShare)
        }
    }
}

@Composable
private fun ExportResultSection(result: ExportResult, onShare: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    when (result) {
        is ExportResult.Success -> {
            Text(result.message, style = MaterialTheme.typography.bodyMedium, color = colors.accentPositive)
            Spacer(Modifier.height(12.dp))
            BrutalButton(onClick = onShare, backgroundColor = colors.surface) { Text(stringResource(Res.string.export_share_button)) }
        }
        is ExportResult.Error -> {
            Text(result.message, style = MaterialTheme.typography.bodyMedium, color = colors.accentError)
        }
    }
}

/**
 * Mismo patrón que el `DateField` privado de ExpenseFormScreen (no se
 * unificaron — no forma parte de este pedido, ver convenciones del
 * proyecto sobre no tocar de más).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDateField(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = LocalSeeBudgetColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .clickable { showPicker = true }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(date.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.export_date_change_label), style = MaterialTheme.typography.labelMedium)
    }

    if (showPicker) {
        val initialMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val selected = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        onDateChange(selected)
                    }
                    showPicker = false
                }) { Text(stringResource(Res.string.export_date_ok_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.export_date_cancel_button)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
