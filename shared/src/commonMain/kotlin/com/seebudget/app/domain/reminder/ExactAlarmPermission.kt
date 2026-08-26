package com.seebudget.app.domain.reminder

/**
 * RF-05 — permiso especial "Alarmas y recordatorios" (Android 12+,
 * `SCHEDULE_EXACT_ALARM`): a diferencia de `POST_NOTIFICATIONS`, Android
 * no lo pide con un diálogo estándar de la app — hay que mandar al
 * usuario a una pantalla del sistema (Ajustes) para que lo conceda a
 * mano, y no hay forma de que la app se entere sola cuando vuelve. Por
 * eso `isGranted()` se vuelve a consultar a pedido (botón "Verificar" en
 * Ajustes, ver `SettingsScreen`/`SettingsViewModel`) en vez de ser un
 * `StateFlow` reactivo.
 *
 * Mismo patrón que `ReminderScheduler`/`FileExporter` (Fase 8): interfaz
 * en `commonMain`, implementación real solo en Android
 * (`ExactAlarmPermissionAndroid`); el resto de plataformas usa
 * [NoOpExactAlarmPermission] — `isGranted()` siempre `true` porque ahí no
 * existe esta restricción, así que la UI de Ajustes no tiene nada que
 * pedir (ver KDoc de `ReminderScheduler` sobre "Android primero").
 */
interface ExactAlarmPermission {
    /** `true` si la plataforma no tiene esta restricción, o si el usuario ya lo concedió. */
    fun isGranted(): Boolean

    /** Lleva al usuario a la pantalla del sistema donde puede concederlo. No-op donde [isGranted] ya es siempre `true`. */
    fun requestGrant()
}

/** RF-05 — sin esta restricción en esta plataforma (ver KDoc de la interfaz). */
class NoOpExactAlarmPermission : ExactAlarmPermission {
    override fun isGranted(): Boolean = true
    override fun requestGrant() = Unit
}
