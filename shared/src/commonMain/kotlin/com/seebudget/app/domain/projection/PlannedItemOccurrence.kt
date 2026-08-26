package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.PlannedItemFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * RF-09/RF-10 — regla de "¿este ítem planificado ocurre en esta fecha?".
 * Extraída de `ProjectionEngine` en Fase 7 (antes vivía como función
 * privada ahí, `occursOn`/`effectiveBillingDay`) para que el Check-in
 * diario (RF-10) pueda hacerse la misma pregunta sobre un `PlannedItem`
 * de dominio sin duplicar la regla — `ProjectionEngine.occursOn` ahora
 * delega acá. "Qué corresponde hoy" en el Check-in nunca debe divergir
 * de "qué se proyecta hoy" en el Dashboard: es la misma regla, un solo
 * lugar.
 *
 * Recibe los campos sueltos (`frequency`/`billingDay`/`specificDate`/
 * `dayOfWeek`) en vez de un `PlannedItem` o un `ProjectionTemplateItem`
 * completo, para no acoplarse a ninguno de los dos tipos que hoy la
 * necesitan — ambos tienen esos mismos campos, con nombres distintos de
 * clase.
 */
object PlannedItemOccurrence {
    fun occursOn(
        frequency: PlannedItemFrequency,
        billingDay: Int?,
        specificDate: LocalDate?,
        dayOfWeek: DayOfWeek?,
        date: LocalDate,
    ): Boolean = when (frequency) {
        PlannedItemFrequency.ONCE -> specificDate == date
        PlannedItemFrequency.DAILY -> true
        PlannedItemFrequency.WEEKLY -> dayOfWeek == date.dayOfWeek
        PlannedItemFrequency.WEEKDAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        PlannedItemFrequency.MONTHLY -> billingDay != null && date.dayOfMonth == effectiveBillingDay(billingDay, date)
    }

    /**
     * Ajuste de mes corto (RF-09: "billingDay... con ajuste automático en
     * meses cortos"): si `billingDay` (1-31) excede la cantidad de días
     * de ese mes puntual (ej. 31 en febrero), la ocurrencia cae en el
     * último día de ese mes.
     */
    fun effectiveBillingDay(billingDay: Int, date: LocalDate): Int = minOf(billingDay, lastDayOfMonth(date))

    private fun lastDayOfMonth(date: LocalDate): Int =
        LocalDate(date.year, date.monthNumber, 1)
            .plus(1, DateTimeUnit.MONTH)
            .minus(1, DateTimeUnit.DAY)
            .dayOfMonth
}
