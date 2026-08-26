package com.seebudget.app.domain.export

/**
 * RF-06 — arma el CSV del historial de gastos, sin depender de ninguna
 * API de plataforma (mismo criterio que `ProjectionEngine`: motor puro
 * en `commonMain`, testeable sin Android/iOS/etc.). El formateo de cada
 * fila (fechas, Ingreso/Gasto, montos, etc.) ya lo resolvió
 * `ExpenseExportRow` — compartido con `ExpensePdfBuilder` — esta clase
 * solo sabe unir esas filas en texto CSV.
 *
 * Encoding: UTF-8 con BOM al inicio — sin el BOM, Excel en Windows
 * interpreta el archivo como ANSI y rompe los acentos ("Categoría",
 * "Método de pago").
 */
object ExpenseCsvExporter {
    private const val UTF8_BOM = "﻿"

    /**
     * RNF-07 (i18n, agregado en el chat): [header] ya viene armado por
     * `ExportViewModel` (`ExpenseExportRow.header(labels)`, con los
     * labels resueltos vía `getString()`) — este objeto es capa de
     * dominio pura, no resuelve ningún string él mismo.
     */
    fun buildCsv(rows: List<ExpenseExportRow>, header: ExpenseExportRow): String {
        val lines = (listOf(header) + rows)
            .map { row -> row.fields().joinToString(",") { field -> csvEscape(field) } }
        return UTF8_BOM + lines.joinToString("\r\n")
    }

    /** RFC 4180: entrecomilla si el campo tiene coma, comilla o salto de línea; duplica comillas internas. */
    private fun csvEscape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
}
