package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.PlannedItemFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RNF-06 — misma regla que antes vivía (probada indirectamente) dentro
 * de `ProjectionEngineTest`; acá se testea directo sobre la función
 * extraída, para poder cubrir casos puntuales de ocurrencia sin tener
 * que armar una simulación completa.
 */
class PlannedItemOccurrenceTest {

    @Test
    fun once_matches_only_its_specific_date() {
        val specificDate = LocalDate(2026, 3, 15)
        assertTrue(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.ONCE, billingDay = null, specificDate = specificDate, dayOfWeek = null,
                date = specificDate,
            ),
        )
        assertFalse(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.ONCE, billingDay = null, specificDate = specificDate, dayOfWeek = null,
                date = specificDate.plus(1, DateTimeUnit.DAY),
            ),
        )
    }

    @Test
    fun once_without_specific_date_never_occurs() {
        assertFalse(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.ONCE, billingDay = null, specificDate = null, dayOfWeek = null,
                date = LocalDate(2026, 3, 15),
            ),
        )
    }

    @Test
    fun weekly_matches_only_its_day_of_week() {
        // 2026-03-16 es lunes.
        val monday = LocalDate(2026, 3, 16)
        assertTrue(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.WEEKLY, billingDay = null, specificDate = null, dayOfWeek = DayOfWeek.MONDAY,
                date = monday,
            ),
        )
        assertFalse(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.WEEKLY, billingDay = null, specificDate = null, dayOfWeek = DayOfWeek.TUESDAY,
                date = monday,
            ),
        )
    }

    @Test
    fun daily_matches_every_day_including_weekend() {
        // 2026-03-16 lunes .. 2026-03-22 domingo.
        val monday = LocalDate(2026, 3, 16)
        val results = (0..6).map { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.DAILY, billingDay = null, specificDate = null, dayOfWeek = null, date = date,
            )
        }
        assertEquals(List(7) { true }, results)
    }

    @Test
    fun weekdays_matches_monday_to_friday_only() {
        // 2026-03-16 lunes .. 2026-03-22 domingo.
        val monday = LocalDate(2026, 3, 16)
        val results = (0..6).map { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            date.dayOfWeek to PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.WEEKDAYS, billingDay = null, specificDate = null, dayOfWeek = null, date = date,
            )
        }
        assertEquals(
            listOf(
                DayOfWeek.MONDAY to true,
                DayOfWeek.TUESDAY to true,
                DayOfWeek.WEDNESDAY to true,
                DayOfWeek.THURSDAY to true,
                DayOfWeek.FRIDAY to true,
                DayOfWeek.SATURDAY to false,
                DayOfWeek.SUNDAY to false,
            ),
            results,
        )
    }

    @Test
    fun monthly_matches_its_billing_day() {
        assertTrue(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.MONTHLY, billingDay = 5, specificDate = null, dayOfWeek = null,
                date = LocalDate(2026, 4, 5),
            ),
        )
        assertFalse(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.MONTHLY, billingDay = 5, specificDate = null, dayOfWeek = null,
                date = LocalDate(2026, 4, 6),
            ),
        )
    }

    @Test
    fun monthly_without_billing_day_never_occurs() {
        assertFalse(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.MONTHLY, billingDay = null, specificDate = null, dayOfWeek = null,
                date = LocalDate(2026, 4, 5),
            ),
        )
    }

    @Test
    fun monthly_billing_day_31_clamps_to_last_day_of_short_month() {
        // Febrero 2026 (no bisiesto) tiene 28 días.
        assertTrue(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.MONTHLY, billingDay = 31, specificDate = null, dayOfWeek = null,
                date = LocalDate(2026, 2, 28),
            ),
        )
        assertFalse(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.MONTHLY, billingDay = 31, specificDate = null, dayOfWeek = null,
                date = LocalDate(2026, 2, 27),
            ),
        )
    }

    @Test
    fun monthly_billing_day_31_clamps_to_last_day_of_leap_february() {
        // 2028 es bisiesto: febrero tiene 29 días.
        assertTrue(
            PlannedItemOccurrence.occursOn(
                PlannedItemFrequency.MONTHLY, billingDay = 31, specificDate = null, dayOfWeek = null,
                date = LocalDate(2028, 2, 29),
            ),
        )
    }

    @Test
    fun effective_billing_day_returns_the_billing_day_when_the_month_is_long_enough() {
        assertEquals(15, PlannedItemOccurrence.effectiveBillingDay(15, LocalDate(2026, 4, 1)))
    }

    @Test
    fun effective_billing_day_clamps_when_the_month_is_shorter() {
        assertEquals(28, PlannedItemOccurrence.effectiveBillingDay(31, LocalDate(2026, 2, 1)))
    }
}
