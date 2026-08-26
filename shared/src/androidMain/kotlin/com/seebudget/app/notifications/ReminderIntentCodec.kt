package com.seebudget.app.notifications

import android.content.Intent
import com.seebudget.app.domain.reminder.ReminderPlan
import kotlinx.datetime.LocalTime

/**
 * (De)serializa un [ReminderPlan] hacia/desde los extras de un `Intent` —
 * lo usan `ReminderSchedulerAndroid` (al armar el `Intent` de la alarma,
 * ver KDoc de esa clase sobre por qué viaja el plan entero y no solo el
 * tipo) y `ReminderAlarmReceiver` (al recibirla).
 */
private const val EXTRA_TYPE = "com.seebudget.app.reminder.TYPE"
private const val EXTRA_BUDGET_ID = "com.seebudget.app.reminder.BUDGET_ID"
private const val EXTRA_BUDGET_NAME = "com.seebudget.app.reminder.BUDGET_NAME"
private const val EXTRA_TIME = "com.seebudget.app.reminder.TIME"

private const val TYPE_BUDGET_CHECKIN = "BUDGET_CHECKIN"
private const val TYPE_GENERAL = "GENERAL"

fun Intent.putReminderPlan(plan: ReminderPlan): Intent = apply {
    putExtra(EXTRA_TIME, plan.time.toString())
    when (plan) {
        is ReminderPlan.BudgetCheckIn -> {
            putExtra(EXTRA_TYPE, TYPE_BUDGET_CHECKIN)
            putExtra(EXTRA_BUDGET_ID, plan.budgetId)
            putExtra(EXTRA_BUDGET_NAME, plan.budgetName)
        }
        is ReminderPlan.GeneralReminder -> putExtra(EXTRA_TYPE, TYPE_GENERAL)
    }
}

fun Intent.toReminderPlan(): ReminderPlan? {
    val time = getStringExtra(EXTRA_TIME)?.let { LocalTime.parse(it) } ?: return null
    return when (getStringExtra(EXTRA_TYPE)) {
        TYPE_BUDGET_CHECKIN -> {
            val budgetId = getStringExtra(EXTRA_BUDGET_ID) ?: return null
            val budgetName = getStringExtra(EXTRA_BUDGET_NAME) ?: return null
            ReminderPlan.BudgetCheckIn(budgetId = budgetId, budgetName = budgetName, time = time)
        }
        TYPE_GENERAL -> ReminderPlan.GeneralReminder(time = time)
        else -> null
    }
}
