package com.seebudget.app.data.reminder

import com.seebudget.app.domain.reminder.ReminderPlan
import com.seebudget.app.domain.reminder.ReminderPlanner
import com.seebudget.app.domain.reminder.ReminderScheduler
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.AuthSessionState
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * RF-05 — política de "¿cuándo (re)programo la notificación diaria?"
 * (decidido en el chat: al abrir la app, y automáticamente cada vez que
 * cambia algo relevante). Se apoya en que [BudgetRepository.observeActive]/
 * [UserSettingsRepository.observe] son reactivos: un solo `combine` cubre
 * ambos disparadores (cambia el presupuesto activo, cambia su
 * `notificationTime`, o cambia el `dailyReminderTime` general) sin que
 * cada ViewModel que toca esos campos (`BudgetFormViewModel`,
 * `SettingsViewModel`, `BudgetDashboardViewModel.activate/pause/complete`)
 * tenga que acordarse de reprogramar nada — ver `ReminderPlanner` para la
 * regla en sí.
 *
 * Mismo patrón que `SyncTrigger`: pensado para correr desde un
 * `LaunchedEffect(Unit)` en App.kt, `run()` no retorna mientras el
 * llamador lo mantenga vivo. `SignedOut`/`Loading` cancela cualquier
 * notificación programada — no hay usuario para el que armar un plan.
 *
 * **[rescheduleOnce] es la otra mitad del sistema (Android, decidido en
 * el chat):** el reinicio del teléfono (`BOOT_COMPLETED`) y el propio
 * disparo de una alarma (para dejar programado el día siguiente) ocurren
 * sin que Compose esté corriendo, así que no pueden apoyarse en `run()`.
 * Hacen el mismo cálculo de forma puntual, con `.first()` en vez de
 * `collectLatest`, y llaman a esta clase (vía Koin) en vez de duplicar la
 * lógica — ver `BootCompletedReceiver`/`ReminderAlarmReceiver`
 * (androidMain).
 */
class ReminderTrigger(
    private val authRepository: AuthRepository,
    private val budgetRepository: BudgetRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun run() {
        authRepository.observeSession().collectLatest { session ->
            when (session) {
                is AuthSessionState.SignedIn -> {
                    combine(
                        budgetRepository.observeActive(),
                        userSettingsRepository.observe(session.userId),
                    ) { activeBudget, user ->
                        ReminderPlanner.decide(activeBudget, user?.dailyReminderTime)
                    }
                        .distinctUntilChanged()
                        .collectLatest { plan -> apply(plan) }
                }
                AuthSessionState.SignedOut, AuthSessionState.Loading -> scheduler.cancel()
            }
        }
    }

    /** Ver KDoc de la clase. No se queda escuchando — calcula el plan vigente una sola vez y lo aplica. */
    suspend fun rescheduleOnce() {
        val session = authRepository.observeSession().first()
        if (session !is AuthSessionState.SignedIn) {
            scheduler.cancel()
            return
        }
        val activeBudget = budgetRepository.observeActive().first()
        val user = userSettingsRepository.observe(session.userId).first()
        apply(ReminderPlanner.decide(activeBudget, user?.dailyReminderTime))
    }

    private fun apply(plan: ReminderPlan?) {
        if (plan == null) scheduler.cancel() else scheduler.schedule(plan)
    }
}
