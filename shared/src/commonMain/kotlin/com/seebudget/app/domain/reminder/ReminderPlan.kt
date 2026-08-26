package com.seebudget.app.domain.reminder

import kotlinx.datetime.LocalTime

/**
 * RF-05 — qué notificación diaria corresponde hoy, si corresponde
 * alguna. Ver `ReminderPlanner` para la regla completa. Es puro dato:
 * quién arma el texto/ícono/deep-link concreto de la notificación es la
 * capa de plataforma (`ReminderScheduler`), no esta clase.
 */
sealed interface ReminderPlan {
    val time: LocalTime

    /**
     * Con presupuesto activo y `Budget.notificationTime` propio (RF-10):
     * abre el Check-in diario de ese presupuesto. `budgetName` viaja acá
     * (en vez de que la plataforma tenga que ir a buscarlo) porque es lo
     * único que hace falta para el texto de la notificación.
     */
    data class BudgetCheckIn(
        val budgetId: String,
        val budgetName: String,
        override val time: LocalTime,
    ) : ReminderPlan

    /** Sin presupuesto activo (o activo pero sin notificationTime propio) — recordatorio general de registrar gastos. */
    data class GeneralReminder(override val time: LocalTime) : ReminderPlan
}
