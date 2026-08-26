package com.seebudget.app.domain.export

import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemType

/**
 * RF-06 — una fila del historial de gastos ya formateada a texto, en
 * `commonMain` porque la comparten `ExpenseCsvExporter` y
 * `ExpensePdfBuilder` (androidMain, ver RF-06 PDF): ambos formatos
 * muestran exactamente las mismas columnas, así que el formateo (fechas,
 * Ingreso/Gasto, montos siempre positivos — el signo lo da `type`, no
 * `amount`, ver KDoc de `Expense`) vive en un solo lugar en vez de
 * duplicarse por formato.
 *
 * Columnas: Fecha, Tipo, Categoría, Monto, Moneda, Nota, Método de pago.
 * Mismo orden en que vienen de `ExpenseRepository.observeByDateRange`
 * (fecha descendente) — no se reordena.
 */
data class ExpenseExportRow(
    val date: String,
    val type: String,
    val category: String,
    val amount: String,
    val currency: String,
    val note: String,
    val paymentMethod: String,
) {
    /** Mismo orden en las 7 columnas siempre — lo usan tanto el CSV (join por comas) como el PDF (una columna por celda). */
    fun fields(): List<String> = listOf(date, type, category, amount, currency, note, paymentMethod)

    companion object {
        /**
         * Fila de encabezado — mismo shape que una fila de datos para
         * poder reusar `fields()`/dibujarla igual en el PDF.
         *
         * RNF-07 (i18n, agregado en el chat): `domain/export` es capa de
         * dominio, sin dependencias de plataforma/UI (mismo criterio que
         * `ProjectionEngine`) — no puede importar Compose Resources. Los
         * labels ya traducidos los resuelve `ExportViewModel` (con
         * `getString()`) y los recibe acá como parámetro, ver
         * [ExpenseExportLabels].
         */
        fun header(labels: ExpenseExportLabels): ExpenseExportRow = ExpenseExportRow(
            date = labels.dateHeader,
            type = labels.typeHeader,
            category = labels.categoryHeader,
            amount = labels.amountHeader,
            currency = labels.currencyHeader,
            note = labels.noteHeader,
            paymentMethod = labels.paymentMethodHeader,
        )

        fun from(expenses: List<Expense>, categories: List<Category>, labels: ExpenseExportLabels): List<ExpenseExportRow> {
            val categoriesById = categories.associateBy { it.id }
            return expenses.map { expense -> of(expense, categoriesById[expense.categoryId], labels) }
        }

        private fun of(expense: Expense, category: Category?, labels: ExpenseExportLabels) = ExpenseExportRow(
            date = expense.date.toString(),
            type = if (expense.type == PlannedItemType.INCOME) labels.incomeType else labels.expenseType,
            category = category?.name.orEmpty(),
            amount = MoneyFormat.toDecimalString(expense.amount, expense.currency),
            currency = expense.currency,
            note = expense.note.orEmpty(),
            paymentMethod = expense.paymentMethod.orEmpty(),
        )
    }
}

/**
 * RNF-07 (i18n, agregado en el chat) — labels ya traducidos que
 * `ExpenseExportRow.header()`/`.from()` necesitan para armar el
 * encabezado y el tipo ("Ingreso"/"Gasto") de cada fila, pero que
 * `domain/export` no puede resolver por sí mismo (ver KDoc de
 * `ExpenseExportRow.header`). `ExportViewModel` es quien los arma con
 * `getString(Res.string.xxx)` antes de llamar a `from()`/`header()`.
 */
data class ExpenseExportLabels(
    val dateHeader: String,
    val typeHeader: String,
    val categoryHeader: String,
    val amountHeader: String,
    val currencyHeader: String,
    val noteHeader: String,
    val paymentMethodHeader: String,
    val incomeType: String,
    val expenseType: String,
)
