package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.export.ExpenseCsvExporter
import com.seebudget.app.domain.export.ExpenseExportLabels
import com.seebudget.app.domain.export.ExpenseExportRow
import com.seebudget.app.domain.export.ExportResult
import com.seebudget.app.domain.export.FileExporter
import com.seebudget.app.domain.model.ReportPeriod
import com.seebudget.app.domain.model.ReportPeriodType
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.generated.resources.*
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.getString

/**
 * RF-06 — Exportación (sección 3 del documento: "Exportar historial de
 * gastos (rango de fechas) a CSV y PDF"). CSV y PDF comparten el mismo
 * rango de fechas y las mismas filas (`ExpenseExportRow`, ver KDoc) —
 * la única diferencia es a qué método de `FileExporter` se las pasa
 * (mismo criterio que RF-05: "Android primero" — ver KDoc de
 * `FileExporter`).
 *
 * Rango de fechas por defecto: el mes actual completo hasta hoy (mismo
 * mes de referencia que usa `ReportsViewModel` por defecto) — un punto
 * de partida razonable que el usuario puede ajustar libremente con los
 * selectores de fecha, no una regla de negocio nueva.
 *
 * `expenseRepository.observeByDateRange` ya devuelve TODOS los gastos
 * (ingresos y egresos, ver `Expense.type`) del rango — el export no
 * filtra por tipo, a diferencia de `ReportsViewModel.kind`: acá el
 * pedido es "historial de gastos" completo, no una vista separada.
 */
class ExportViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val fileExporter: FileExporter,
) : ViewModel() {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val defaultFrom = ReportPeriod(ReportPeriodType.MONTH, today).range.start

    private val _from = MutableStateFlow(defaultFrom)
    val from: StateFlow<LocalDate> = _from.asStateFlow()

    private val _to = MutableStateFlow(today)
    val to: StateFlow<LocalDate> = _to.asStateFlow()

    private val _status = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val status: StateFlow<ExportStatus> = _status.asStateFlow()

    fun onFromChange(date: LocalDate) {
        _from.value = date
        clearStaleStatus()
    }

    fun onToChange(date: LocalDate) {
        _to.value = date
        clearStaleStatus()
    }

    /** Un cambio de rango invalida el mensaje de éxito/error de la exportación anterior. */
    private fun clearStaleStatus() {
        if (_status.value is ExportStatus.Done || _status.value == ExportStatus.InvalidDateRange) {
            _status.value = ExportStatus.Idle
        }
    }

    fun onExportCsv() = export(ExportFormat.CSV)

    fun onExportPdf() = export(ExportFormat.PDF)

    private fun export(format: ExportFormat) {
        val from = _from.value
        val to = _to.value
        if (from > to) {
            // RNF-07 (i18n, agregado en el chat): este es un mensaje propio
            // (no viene de una excepción ni de FileExporter), así que viaja
            // como estado sin texto armado — ExportScreen lo resuelve con
            // stringResource() — mismo criterio que AuthErrorMessage/LoginScreen.
            _status.value = ExportStatus.InvalidDateRange
            return
        }
        viewModelScope.launch {
            _status.value = ExportStatus.Exporting
            val expenses = expenseRepository.observeByDateRange(from, to).first()
            val categories = categoryRepository.observeAll().first()
            // RNF-07 (i18n, agregado en el chat, gap encontrado en el chat):
            // domain/export es capa de dominio pura, sin Compose Resources
            // (mismo criterio que ProjectionEngine) — los labels de columnas
            // y de tipo se resuelven acá con getString() y se pasan como
            // parámetro, ver KDoc de ExpenseExportRow.header/ExpenseExportLabels.
            val labels = ExpenseExportLabels(
                dateHeader = getString(Res.string.export_header_date),
                typeHeader = getString(Res.string.export_header_type),
                categoryHeader = getString(Res.string.export_header_category),
                amountHeader = getString(Res.string.export_header_amount),
                currencyHeader = getString(Res.string.export_header_currency),
                noteHeader = getString(Res.string.export_header_note),
                paymentMethodHeader = getString(Res.string.export_header_payment_method),
                incomeType = getString(Res.string.export_type_income),
                expenseType = getString(Res.string.export_type_expense),
            )
            val header = ExpenseExportRow.header(labels)
            val rows = ExpenseExportRow.from(expenses, categories, labels)
            val result = when (format) {
                ExportFormat.CSV -> fileExporter.exportCsv(
                    fileName = "gastos_${from}_${to}.csv",
                    content = ExpenseCsvExporter.buildCsv(rows, header),
                )
                ExportFormat.PDF -> fileExporter.exportPdf(
                    fileName = "gastos_${from}_${to}.pdf",
                    title = getString(Res.string.export_pdf_title),
                    subtitle = getString(Res.string.export_pdf_subtitle, from, to),
                    header = header,
                    rows = rows,
                )
            }
            _status.value = ExportStatus.Done(result)
        }
    }

    fun onShare() {
        val handle = (_status.value as? ExportStatus.Done)?.result as? ExportResult.Success ?: return
        // RNF-07 (i18n, agregado en el chat): getString() es suspend, así que
        // esto ahora corre en viewModelScope (antes era una llamada directa) —
        // ver KDoc de FileExporter.share.
        viewModelScope.launch {
            val chooserTitle = getString(Res.string.export_share_chooser_title)
            fileExporter.share(handle.handle, chooserTitle)
        }
    }

    private enum class ExportFormat { CSV, PDF }
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Exporting : ExportStatus

    /** RNF-07 (i18n): rango de fechas inválido — ver KDoc de `ExportViewModel.export`. */
    data object InvalidDateRange : ExportStatus
    data class Done(val result: ExportResult) : ExportStatus
}
