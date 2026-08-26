package com.seebudget.app.domain.reminder

import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.model.SyncStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RF-05 — cobertura de la regla "una reemplaza a la otra según haya o no presupuesto activo". */
class ReminderPlannerTest {

    private fun budget(notificationTime: LocalTime?) = Budget(
        id = "budget-1",
        userId = "user",
        name = "Presupuesto de prueba",
        startDate = LocalDate(2026, 1, 1),
        endDate = null,
        status = BudgetStatus.ACTIVE,
        initialBalance = 0,
        baseCurrency = "USD",
        notificationTime = notificationTime,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        syncStatus = SyncStatus.SYNCED,
        deletedAt = null,
    )

    @Test
    fun no_active_budget_uses_general_reminder_when_configured() {
        val plan = ReminderPlanner.decide(activeBudget = null, dailyReminderTime = LocalTime(9, 0))

        assertEquals(ReminderPlan.GeneralReminder(LocalTime(9, 0)), plan)
    }

    @Test
    fun no_active_budget_and_no_general_reminder_configured_means_no_plan() {
        val plan = ReminderPlanner.decide(activeBudget = null, dailyReminderTime = null)

        assertNull(plan)
    }

    @Test
    fun active_budget_with_its_own_notification_time_wins_over_general_reminder() {
        val plan = ReminderPlanner.decide(
            activeBudget = budget(notificationTime = LocalTime(20, 0)),
            dailyReminderTime = LocalTime(9, 0),
        )

        assertEquals(ReminderPlan.BudgetCheckIn("budget-1", "Presupuesto de prueba", LocalTime(20, 0)), plan)
    }

    @Test
    fun active_budget_without_its_own_notification_time_falls_back_to_general_reminder() {
        val plan = ReminderPlanner.decide(
            activeBudget = budget(notificationTime = null),
            dailyReminderTime = LocalTime(9, 0),
        )

        assertEquals(ReminderPlan.GeneralReminder(LocalTime(9, 0)), plan)
    }

    @Test
    fun active_budget_without_notification_time_and_no_general_reminder_means_no_plan() {
        val plan = ReminderPlanner.decide(activeBudget = budget(notificationTime = null), dailyReminderTime = null)

        assertNull(plan)
    }
}
