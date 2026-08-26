package com.seebudget.app.domain.reminder

/**
 * Puente hacia la API de notificaciones/alarmas de cada plataforma —
 * mismo criterio que `SqlDriver`/`HttpClient` (Fase 1/2): la interfaz
 * vive en `commonMain`, la implementación real se registra por
 * plataforma en `platformModule()`. Fase 8 ("Solo Android" para la
 * primera pasada real, decidido en el chat): Android registra
 * `ReminderSchedulerAndroid` (ver androidMain); iOS/Desktop/Web
 * registran [NoOpReminderScheduler] — la app compila y corre en las 5
 * plataformas, pero solo Android efectivamente notifica.
 *
 * `ReminderTrigger` (data/reminder) es el único llamador — decide QUÉ
 * programar (`ReminderPlan`, vía `ReminderPlanner`); esta interfaz solo
 * sabe CÓMO programarlo en la plataforma actual.
 */
interface ReminderScheduler {
    /**
     * Programa (reemplazando cualquier notificación diaria programada
     * antes) la descrita por [plan]. Idempotente: se llama de nuevo cada
     * vez que algo relevante cambia (ver KDoc de `ReminderTrigger`), no
     * solo la primera vez.
     */
    fun schedule(plan: ReminderPlan)

    /** Cancela cualquier notificación diaria programada — no queda ninguna vigente (ver `ReminderPlanner`, caso "ninguno configurado"). */
    fun cancel()
}

/** Fase 8 — Android es la única plataforma con implementación real por ahora (ver KDoc de la interfaz). */
class NoOpReminderScheduler : ReminderScheduler {
    override fun schedule(plan: ReminderPlan) = Unit
    override fun cancel() = Unit
}
