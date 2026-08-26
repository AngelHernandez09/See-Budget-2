package com.seebudget.app.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** RF-03 — granularidad del reporte (sección 8.4 #4, "selector de periodo"). */
enum class ReportPeriodType { DAY, WEEK, MONTH, YEAR }

/**
 * RF-03 — periodo de un reporte, con navegación anterior/siguiente
 * (sección 8.4 #4: "navegación anterior/siguiente").
 *
 * `referenceDate` SIEMPRE se normaliza al inicio del periodo (lunes de esa
 * semana / día 1 del mes / 1 de enero del año) — evita casos borde al
 * navegar (ej. "31 de enero" menos 1 mes no es una fecha válida en todos
 * los meses; normalizando siempre a día 1, restar/sumar 1 mes es seguro).
 *
 * Semana: Lunes-Domingo (ISO 8601) — el documento no fija una convención;
 * es la que usa la mayoría de los países hispanohablantes.
 */
data class ReportPeriod(val type: ReportPeriodType, private val rawReferenceDate: LocalDate) {
    val referenceDate: LocalDate = normalize(type, rawReferenceDate)

    val range: ClosedRange<LocalDate>
        get() = when (type) {
            ReportPeriodType.DAY -> referenceDate..referenceDate
            ReportPeriodType.WEEK -> referenceDate..referenceDate.plus(6, DateTimeUnit.DAY)
            ReportPeriodType.MONTH ->
                referenceDate..referenceDate.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            ReportPeriodType.YEAR ->
                referenceDate..referenceDate.plus(1, DateTimeUnit.YEAR).minus(1, DateTimeUnit.DAY)
        }

    fun previous(): ReportPeriod = ReportPeriod(type, shift(-1))
    fun next(): ReportPeriod = ReportPeriod(type, shift(1))

    private fun shift(amount: Int): LocalDate = when (type) {
        ReportPeriodType.DAY -> referenceDate.plus(amount, DateTimeUnit.DAY)
        ReportPeriodType.WEEK -> referenceDate.plus(amount * 7, DateTimeUnit.DAY)
        ReportPeriodType.MONTH -> referenceDate.plus(amount, DateTimeUnit.MONTH)
        ReportPeriodType.YEAR -> referenceDate.plus(amount, DateTimeUnit.YEAR)
    }

    companion object {
        private fun normalize(type: ReportPeriodType, date: LocalDate): LocalDate = when (type) {
            ReportPeriodType.DAY -> date
            ReportPeriodType.WEEK -> date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
            ReportPeriodType.MONTH -> LocalDate(date.year, date.monthNumber, 1)
            ReportPeriodType.YEAR -> LocalDate(date.year, 1, 1)
        }
    }
}
