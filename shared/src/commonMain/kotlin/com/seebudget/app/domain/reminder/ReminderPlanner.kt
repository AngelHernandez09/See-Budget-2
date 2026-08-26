package com.seebudget.app.domain.reminder

import com.seebudget.app.domain.model.Budget
import kotlinx.datetime.LocalTime

/**
 * RF-05 — Recordatorios: "notificación diaria única y configurable".
 * Pura, sin dependencias de plataforma — mismo criterio que
 * `PlannedItemOccurrence`/`CheckInDayBuilder` (Fase 7).
 *
 * Regla (documento, sección RF-05, + un caso no cubierto ahí resuelto en
 * el chat):
 * - Presupuesto activo CON `notificationTime` propio → esa notificación
 *   (abre el Check-in de ese presupuesto). Nunca se combina con la
 *   general el mismo día — "una reemplaza a la otra según haya o no
 *   presupuesto activo".
 * - Presupuesto activo SIN `notificationTime` propio (el documento no
 *   contempla este caso — el usuario activó ese presupuesto sin
 *   configurarle una hora de check-in; decidido en el chat) → cae al
 *   recordatorio general (`dailyReminderTime`), igual que si no hubiera
 *   presupuesto activo.
 * - Sin presupuesto activo → recordatorio general (`dailyReminderTime`).
 * - Ninguno configurado → sin notificación ese día.
 */
object ReminderPlanner {
    fun decide(activeBudget: Budget?, dailyReminderTime: LocalTime?): ReminderPlan? {
        val budgetTime = activeBudget?.notificationTime
        return when {
            activeBudget != null && budgetTime != null -> ReminderPlan.BudgetCheckIn(
                budgetId = activeBudget.id,
                budgetName = activeBudget.name,
                time = budgetTime,
            )
            dailyReminderTime != null -> ReminderPlan.GeneralReminder(dailyReminderTime)
            else -> null
        }
    }
}
