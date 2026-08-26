package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.SyncStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RNF-06 — RF-10 (Check-in diario): cobertura de qué ítems corresponden a un día y cómo se emparejan con los Expense ya confirmados. */
class CheckInDayBuilderTest {

    private fun plannedItem(
        id: String = "item-1",
        name: String = "Sueldo",
        type: PlannedItemType = PlannedItemType.INCOME,
        frequency: PlannedItemFrequency = PlannedItemFrequency.MONTHLY,
        billingDay: Int? = 5,
        specificDate: LocalDate? = null,
        dayOfWeek: kotlinx.datetime.DayOfWeek? = null,
    ) = PlannedItem(
        id = id,
        budgetId = "budget",
        userId = "user",
        name = name,
        categoryId = "category",
        type = type,
        amount = 1_000,
        currency = "USD",
        frequency = frequency,
        billingDay = billingDay,
        specificDate = specificDate,
        dayOfWeek = dayOfWeek,
        isActive = true,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        syncStatus = SyncStatus.SYNCED,
        deletedAt = null,
    )

    private fun expense(
        id: String = "expense-1",
        date: LocalDate,
        plannedItemId: String? = null,
        type: PlannedItemType = PlannedItemType.EXPENSE,
    ) = Expense(
        id = id,
        userId = "user",
        amount = 1_000,
        currency = "USD",
        categoryId = "category",
        date = date,
        note = null,
        receiptImageUrl = null,
        paymentMethod = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        syncStatus = SyncStatus.SYNCED,
        budgetId = "budget",
        plannedItemId = plannedItemId,
        isPlannedOverride = false,
        deletedAt = null,
        type = type,
    )

    @Test
    fun includes_only_planned_items_that_occur_on_that_date() {
        val date = LocalDate(2026, 4, 5) // billingDay 5
        val matches = plannedItem(id = "matches", billingDay = 5)
        val doesNotMatch = plannedItem(id = "does-not-match", billingDay = 6)

        val day = CheckInDayBuilder.build(
            date = date,
            activePlannedItems = listOf(matches, doesNotMatch),
            expensesForDay = emptyList(),
        )

        assertEquals(listOf("matches"), day.items.map { it.plannedItem.id })
    }

    @Test
    fun items_without_a_matching_expense_start_unconfirmed() {
        val date = LocalDate(2026, 4, 5)
        val day = CheckInDayBuilder.build(
            date = date,
            activePlannedItems = listOf(plannedItem(id = "item-1", billingDay = 5)),
            expensesForDay = emptyList(),
        )

        val item = day.items.single()
        assertFalse(item.isConfirmed)
        assertNull(item.confirmedExpense)
    }

    @Test
    fun pairs_an_item_with_its_confirmed_expense_by_planned_item_id() {
        val date = LocalDate(2026, 4, 5)
        val confirmedExpense = expense(id = "expense-1", date = date, plannedItemId = "item-1")

        val day = CheckInDayBuilder.build(
            date = date,
            activePlannedItems = listOf(plannedItem(id = "item-1", billingDay = 5)),
            expensesForDay = listOf(confirmedExpense),
        )

        val item = day.items.single()
        assertTrue(item.isConfirmed)
        assertEquals("expense-1", item.confirmedExpense?.id)
    }

    @Test
    fun when_multiple_expenses_match_the_same_planned_item_takes_the_first() {
        val date = LocalDate(2026, 4, 5)
        val first = expense(id = "first", date = date, plannedItemId = "item-1")
        val second = expense(id = "second", date = date, plannedItemId = "item-1")

        val day = CheckInDayBuilder.build(
            date = date,
            activePlannedItems = listOf(plannedItem(id = "item-1", billingDay = 5)),
            expensesForDay = listOf(first, second),
        )

        assertEquals("first", day.items.single().confirmedExpense?.id)
    }

    @Test
    fun separates_expenses_with_no_planned_item_id_as_additional_expenses() {
        val date = LocalDate(2026, 4, 5)
        val additional = expense(id = "additional", date = date, plannedItemId = null)
        val linked = expense(id = "linked", date = date, plannedItemId = "item-1")

        val day = CheckInDayBuilder.build(
            date = date,
            activePlannedItems = listOf(plannedItem(id = "item-1", billingDay = 5)),
            expensesForDay = listOf(additional, linked),
        )

        assertEquals(listOf("additional"), day.additionalExpenses.map { it.id })
    }

    @Test
    fun day_with_no_occurring_items_and_no_expenses_is_empty() {
        val date = LocalDate(2026, 4, 6) // no matchea billingDay = 5
        val day = CheckInDayBuilder.build(
            date = date,
            activePlannedItems = listOf(plannedItem(id = "item-1", billingDay = 5)),
            expensesForDay = emptyList(),
        )

        assertTrue(day.items.isEmpty())
        assertTrue(day.additionalExpenses.isEmpty())
    }

    @Test
    fun a_once_frequency_item_only_occurs_on_its_specific_date() {
        val specificDate = LocalDate(2026, 4, 10)
        val item = plannedItem(id = "item-1", frequency = PlannedItemFrequency.ONCE, billingDay = null, specificDate = specificDate)

        val onDate = CheckInDayBuilder.build(date = specificDate, activePlannedItems = listOf(item), expensesForDay = emptyList())
        val offDate = CheckInDayBuilder.build(date = LocalDate(2026, 4, 11), activePlannedItems = listOf(item), expensesForDay = emptyList())

        assertEquals(1, onDate.items.size)
        assertTrue(offDate.items.isEmpty())
    }
}
