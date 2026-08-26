package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.ProjectionSnapshot
import com.seebudget.app.domain.model.ProjectionSnapshotItem
import com.seebudget.app.domain.model.SyncStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RNF-06 — mismo criterio de cobertura exhaustiva que ProjectionEngineTest, para el diff de "Historial de proyecciones" (RF-09). */
class ProjectionSnapshotComparerTest {

    private fun item(
        id: String = "item-1",
        amount: Long = 1_000,
        isActive: Boolean = true,
    ) = ProjectionSnapshotItem(
        plannedItemId = id,
        name = "Alquiler",
        type = PlannedItemType.EXPENSE,
        amount = amount,
        currency = "USD",
        frequency = PlannedItemFrequency.MONTHLY,
        billingDay = 5,
        specificDate = null,
        dayOfWeek = null,
        isActive = isActive,
    )

    private fun snapshot(
        initialBalance: Long = 10_000,
        startDate: LocalDate = LocalDate(2026, 1, 1),
        baseCurrency: String = "USD",
        items: List<ProjectionSnapshotItem> = listOf(item()),
    ) = ProjectionSnapshot(
        id = "snap",
        budgetId = "budget",
        userId = "user",
        label = "Test",
        frozenInitialBalance = initialBalance,
        frozenStartDate = startDate,
        frozenBaseCurrency = baseCurrency,
        frozenPlannedItems = items,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        syncStatus = SyncStatus.SYNCED,
        deletedAt = null,
    )

    @Test
    fun identical_snapshots_have_no_changes() {
        val diff = ProjectionSnapshotComparer.diff(snapshot(), snapshot())
        assertFalse(diff.hasChanges)
        assertNull(diff.initialBalanceChange)
        assertNull(diff.startDateChange)
        assertNull(diff.baseCurrencyChange)
        assertTrue(diff.addedItems.isEmpty())
        assertTrue(diff.removedItems.isEmpty())
        assertTrue(diff.changedItems.isEmpty())
    }

    @Test
    fun detects_initial_balance_change() {
        val diff = ProjectionSnapshotComparer.diff(snapshot(initialBalance = 10_000), snapshot(initialBalance = 5_000))
        assertEquals(Change(10_000L, 5_000L), diff.initialBalanceChange)
        assertTrue(diff.hasChanges)
    }

    @Test
    fun detects_start_date_change() {
        val diff = ProjectionSnapshotComparer.diff(
            snapshot(startDate = LocalDate(2026, 1, 1)),
            snapshot(startDate = LocalDate(2026, 2, 1)),
        )
        assertEquals(Change(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1)), diff.startDateChange)
    }

    @Test
    fun detects_base_currency_change() {
        val diff = ProjectionSnapshotComparer.diff(snapshot(baseCurrency = "USD"), snapshot(baseCurrency = "EUR"))
        assertEquals(Change("USD", "EUR"), diff.baseCurrencyChange)
    }

    @Test
    fun detects_added_item() {
        val diff = ProjectionSnapshotComparer.diff(
            snapshot(items = listOf(item(id = "a"))),
            snapshot(items = listOf(item(id = "a"), item(id = "b"))),
        )
        assertEquals(listOf("b"), diff.addedItems.map { it.plannedItemId })
        assertTrue(diff.removedItems.isEmpty())
        assertTrue(diff.changedItems.isEmpty())
    }

    @Test
    fun detects_removed_item() {
        val diff = ProjectionSnapshotComparer.diff(
            snapshot(items = listOf(item(id = "a"), item(id = "b"))),
            snapshot(items = listOf(item(id = "a"))),
        )
        assertEquals(listOf("b"), diff.removedItems.map { it.plannedItemId })
        assertTrue(diff.addedItems.isEmpty())
    }

    @Test
    fun detects_changed_item_amount() {
        val diff = ProjectionSnapshotComparer.diff(
            snapshot(items = listOf(item(id = "a", amount = 1_000))),
            snapshot(items = listOf(item(id = "a", amount = 1_500))),
        )
        assertEquals(1, diff.changedItems.size)
        assertEquals(1_000L, diff.changedItems.single().previous.amount)
        assertEquals(1_500L, diff.changedItems.single().current.amount)
    }

    @Test
    fun detects_item_deactivated() {
        val diff = ProjectionSnapshotComparer.diff(
            snapshot(items = listOf(item(id = "a", isActive = true))),
            snapshot(items = listOf(item(id = "a", isActive = false))),
        )
        assertEquals(1, diff.changedItems.size)
        assertTrue(diff.changedItems.single().previous.isActive)
        assertFalse(diff.changedItems.single().current.isActive)
    }

    @Test
    fun same_id_and_same_fields_is_not_reported_as_changed() {
        val diff = ProjectionSnapshotComparer.diff(
            snapshot(items = listOf(item(id = "a", amount = 1_000))),
            snapshot(items = listOf(item(id = "a", amount = 1_000))),
        )
        assertTrue(diff.changedItems.isEmpty())
        assertFalse(diff.hasChanges)
    }
}
