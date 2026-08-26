package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.plus

/**
 * RNF-06 — "cobertura de tests unitarios... incluyendo casos exhaustivos
 * del ProjectionEngine (frecuencias, meses cortos, ediciones
 * retroactivas)". `ediciones retroactivas` en sí (recalcular la línea
 * real al editar un día pasado del check-in) es de Fase 7 — acá se cubre
 * lo que ya existe hoy: frecuencias, ajuste de mes corto, split
 * actuals/plantilla en `today`, y detección de quiebre de saldo.
 */
class ProjectionEngineTest {

    private fun monthly(amount: Long, billingDay: Int, type: PlannedItemType = PlannedItemType.EXPENSE) =
        ProjectionTemplateItem(
            plannedItemId = "monthly",
            type = type,
            amount = amount,
            frequency = PlannedItemFrequency.MONTHLY,
            billingDay = billingDay,
        )

    private fun weekly(amount: Long, dayOfWeek: DayOfWeek, type: PlannedItemType = PlannedItemType.EXPENSE) =
        ProjectionTemplateItem(
            plannedItemId = "weekly",
            type = type,
            amount = amount,
            frequency = PlannedItemFrequency.WEEKLY,
            dayOfWeek = dayOfWeek,
        )

    private fun weekdays(amount: Long, type: PlannedItemType = PlannedItemType.EXPENSE) =
        ProjectionTemplateItem(
            plannedItemId = "weekdays",
            type = type,
            amount = amount,
            frequency = PlannedItemFrequency.WEEKDAYS,
        )

    private fun once(amount: Long, specificDate: LocalDate, type: PlannedItemType = PlannedItemType.EXPENSE) =
        ProjectionTemplateItem(
            plannedItemId = "once",
            type = type,
            amount = amount,
            frequency = PlannedItemFrequency.ONCE,
            specificDate = specificDate,
        )

    private fun ProjectionResult.balanceOn(date: LocalDate): Long =
        days.first { it.date == date }.balance

    // ---------------------------------------------------------------
    // MONTHLY — ocurrencia básica y ajuste de mes corto
    // ---------------------------------------------------------------

    @Test
    fun monthly_occurs_only_on_billing_day() {
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 1, 31),
            initialBalance = 10_000,
            template = listOf(monthly(amount = 1_000, billingDay = 15)),
        )
        assertEquals(10_000, result.balanceOn(LocalDate(2026, 1, 14)))
        assertEquals(9_000, result.balanceOn(LocalDate(2026, 1, 15)))
        assertEquals(9_000, result.balanceOn(LocalDate(2026, 1, 16)))
    }

    @Test
    fun monthly_billing_day_31_falls_on_last_day_of_short_month() {
        // Abril tiene 30 días — billingDay=31 debe caer el 30.
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 4, 1),
            endDate = LocalDate(2026, 4, 30),
            initialBalance = 5_000,
            template = listOf(monthly(amount = 500, billingDay = 31)),
        )
        assertEquals(5_000, result.balanceOn(LocalDate(2026, 4, 29)))
        assertEquals(4_500, result.balanceOn(LocalDate(2026, 4, 30)))
    }

    @Test
    fun monthly_billing_day_31_falls_on_feb_28_in_non_leap_year() {
        // 2026 no es bisiesto.
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 2, 1),
            endDate = LocalDate(2026, 2, 28),
            initialBalance = 5_000,
            template = listOf(monthly(amount = 500, billingDay = 31)),
        )
        assertEquals(4_500, result.balanceOn(LocalDate(2026, 2, 28)))
    }

    @Test
    fun monthly_billing_day_31_falls_on_feb_29_in_leap_year() {
        // 2028 es bisiesto.
        val result = ProjectionEngine.project(
            startDate = LocalDate(2028, 2, 1),
            endDate = LocalDate(2028, 2, 29),
            initialBalance = 5_000,
            template = listOf(monthly(amount = 500, billingDay = 31)),
        )
        assertEquals(5_000, result.balanceOn(LocalDate(2028, 2, 28)))
        assertEquals(4_500, result.balanceOn(LocalDate(2028, 2, 29)))
    }

    @Test
    fun monthly_recurs_every_month_across_a_full_year() {
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 12, 31),
            initialBalance = 0,
            template = listOf(monthly(amount = 100, billingDay = 1, type = PlannedItemType.INCOME)),
        )
        // Cobra el día 1 de cada uno de los 12 meses.
        assertEquals(1_200, result.days.last().balance)
    }

    // ---------------------------------------------------------------
    // WEEKLY
    // ---------------------------------------------------------------

    @Test
    fun weekly_occurs_only_on_matching_day_of_week() {
        // 2026-01-05 es lunes.
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 1, 31),
            initialBalance = 0,
            template = listOf(weekly(amount = 50, dayOfWeek = DayOfWeek.MONDAY)),
        )
        val mondays = result.days.filter { it.date.dayOfWeek == DayOfWeek.MONDAY }
        assertEquals(4, mondays.size) // 5, 12, 19, 26 de enero 2026
        assertEquals(true, mondays.all { it.delta == -50L })
        assertEquals(true, result.days.filter { it.date.dayOfWeek != DayOfWeek.MONDAY }.all { it.delta == 0L })
    }

    // ---------------------------------------------------------------
    // WEEKDAYS
    // ---------------------------------------------------------------

    @Test
    fun weekdays_excludes_saturday_and_sunday() {
        // 2026-01-01 es jueves; incluye el fin de semana 3-4 enero.
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 1, 7),
            initialBalance = 0,
            template = listOf(weekdays(amount = 10)),
        )
        val weekendDeltas = result.days
            .filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
            .map { it.delta }
        val weekdayDeltas = result.days
            .filterNot { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
            .map { it.delta }
        assertEquals(true, weekendDeltas.all { it == 0L })
        assertEquals(true, weekdayDeltas.all { it == -10L })
    }

    // ---------------------------------------------------------------
    // ONCE
    // ---------------------------------------------------------------

    @Test
    fun once_occurs_exactly_on_specific_date_and_nowhere_else() {
        val target = LocalDate(2026, 3, 15)
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 31),
            initialBalance = 1_000,
            template = listOf(once(amount = 200, specificDate = target)),
        )
        assertEquals(-200, result.days.first { it.date == target }.delta)
        assertEquals(true, result.days.filterNot { it.date == target }.all { it.delta == 0L })
    }

    // ---------------------------------------------------------------
    // INCOME vs EXPENSE
    // ---------------------------------------------------------------

    @Test
    fun income_adds_and_expense_subtracts() {
        val date = LocalDate(2026, 5, 10)
        val result = ProjectionEngine.project(
            startDate = date,
            endDate = date,
            initialBalance = 1_000,
            template = listOf(once(amount = 300, specificDate = date, type = PlannedItemType.INCOME)),
        )
        assertEquals(1_300, result.days.single().balance)
    }

    // ---------------------------------------------------------------
    // Split actuals (pasado/hoy) vs plantilla (futuro)
    // ---------------------------------------------------------------

    @Test
    fun days_up_to_and_including_today_use_actuals_not_template() {
        val today = LocalDate(2026, 6, 10)
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 6, 1),
            endDate = LocalDate(2026, 6, 20),
            initialBalance = 1_000,
            template = listOf(monthly(amount = 999, billingDay = 10)), // pisaría el 10 si no fuera "hoy"
            today = today,
            actualDeltasByDate = mapOf(today to -50L),
        )
        val todayRow = result.days.first { it.date == today }
        assertEquals(false, todayRow.isProjected)
        assertEquals(-50L, todayRow.delta) // el actual, NO el -999 de la plantilla
    }

    @Test
    fun days_after_today_use_template_not_actuals() {
        val today = LocalDate(2026, 6, 10)
        val future = LocalDate(2026, 6, 15)
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 6, 1),
            endDate = LocalDate(2026, 6, 20),
            initialBalance = 1_000,
            template = listOf(once(amount = 300, specificDate = future)),
            today = today,
            actualDeltasByDate = mapOf(future to -1L), // no debería usarse: future > today
        )
        val futureRow = result.days.first { it.date == future }
        assertEquals(true, futureRow.isProjected)
        assertEquals(-300L, futureRow.delta)
    }

    @Test
    fun reference_line_mode_today_null_uses_template_for_every_day_ignoring_actuals() {
        val pastDate = LocalDate(2026, 1, 5)
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 1, 10),
            initialBalance = 1_000,
            template = listOf(once(amount = 100, specificDate = pastDate)),
            today = null, // línea de referencia: todo el rango es "plantilla", incluso días "pasados"
            actualDeltasByDate = mapOf(pastDate to -9_999L), // no debería aplicarse nunca en este modo
        )
        val row = result.days.first { it.date == pastDate }
        assertEquals(true, row.isProjected)
        assertEquals(-100L, row.delta)
    }

    // ---------------------------------------------------------------
    // breakEvenDate
    // ---------------------------------------------------------------

    @Test
    fun break_even_date_is_first_day_balance_drops_to_zero_or_below() {
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 7, 1),
            endDate = LocalDate(2026, 7, 10),
            initialBalance = 100,
            template = listOf(monthly(amount = 60, billingDay = 3)),
        )
        // 100 el día 1-2, cae a 40 el día 3 (no se agota), no hay otra ocurrencia
        // en el rango — nunca llega a <= 0.
        assertNull(result.breakEvenDate)
    }

    @Test
    fun break_even_date_detected_when_balance_is_exhausted() {
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 7, 1),
            endDate = LocalDate(2026, 7, 10),
            initialBalance = 50,
            template = listOf(monthly(amount = 60, billingDay = 3)),
        )
        assertEquals(LocalDate(2026, 7, 3), result.breakEvenDate)
    }

    @Test
    fun break_even_date_is_start_date_when_initial_balance_already_zero_or_negative() {
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 7, 1),
            endDate = LocalDate(2026, 7, 5),
            initialBalance = 0,
            template = emptyList(),
        )
        assertEquals(LocalDate(2026, 7, 1), result.breakEvenDate)
    }

    // ---------------------------------------------------------------
    // Horizonte sin endDate / rango inválido
    // ---------------------------------------------------------------

    @Test
    fun no_end_date_uses_horizon_days_from_today() {
        val today = LocalDate(2026, 1, 1)
        val result = ProjectionEngine.project(
            startDate = today,
            endDate = null,
            initialBalance = 0,
            template = emptyList(),
            today = today,
            horizonDays = 30,
        )
        assertEquals(today.plus(30, kotlinx.datetime.DateTimeUnit.DAY), result.days.last().date)
    }

    @Test
    fun end_date_before_start_date_returns_empty_result() {
        val result = ProjectionEngine.project(
            startDate = LocalDate(2026, 1, 10),
            endDate = LocalDate(2026, 1, 1),
            initialBalance = 100,
            template = emptyList(),
        )
        assertEquals(true, result.days.isEmpty())
        assertNull(result.breakEvenDate)
    }

    // ---------------------------------------------------------------
    // Múltiples ítems el mismo día
    // ---------------------------------------------------------------

    @Test
    fun multiple_template_items_on_the_same_day_are_summed() {
        val date = LocalDate(2026, 8, 1)
        val result = ProjectionEngine.project(
            startDate = date,
            endDate = date,
            initialBalance = 0,
            template = listOf(
                once(amount = 500, specificDate = date, type = PlannedItemType.INCOME),
                once(amount = 120, specificDate = date, type = PlannedItemType.EXPENSE),
            ),
        )
        assertEquals(380, result.days.single().balance)
    }

    // ---------------------------------------------------------------
    // incomeDelta / expenseDelta (Fase 6 UI — "gasto total por mes/año")
    // ---------------------------------------------------------------

    @Test
    fun expense_delta_and_income_delta_split_template_days_correctly() {
        val date = LocalDate(2026, 8, 1)
        val result = ProjectionEngine.project(
            startDate = date,
            endDate = date,
            initialBalance = 0,
            template = listOf(
                once(amount = 500, specificDate = date, type = PlannedItemType.INCOME),
                once(amount = 120, specificDate = date, type = PlannedItemType.EXPENSE),
            ),
        )
        val day = result.days.single()
        assertEquals(500, day.incomeDelta)
        assertEquals(120, day.expenseDelta)
        assertEquals(380, day.delta)
    }

    @Test
    fun expense_delta_on_actual_day_mirrors_negative_delta() {
        val today = LocalDate(2026, 8, 1)
        val result = ProjectionEngine.project(
            startDate = today,
            endDate = today,
            initialBalance = 1_000,
            template = emptyList(),
            today = today,
            actualDeltasByDate = mapOf(today to -300L),
        )
        val day = result.days.single()
        assertEquals(0, day.incomeDelta)
        assertEquals(300, day.expenseDelta)
        assertEquals(-300, day.delta)
    }
}
