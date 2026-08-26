package com.seebudget.app.domain.repository

import com.seebudget.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * RF-04/Fase 3 — perfil y preferencias del usuario (`baseCurrency`,
 * `locale`, `dailyReminderTime`). Ver nota completa en `User.kt` sobre por
 * qué esto antes no existía y cómo se sincroniza.
 */
interface UserSettingsRepository {
    /** Observa el perfil de un usuario puntual — null si todavía no existe la fila. */
    fun observe(userId: String): Flow<User?>

    /**
     * Idempotente: si el usuario logueado todavía no tiene fila de
     * settings, la crea con defaults razonables (baseCurrency = "USD",
     * locale = "es", sin recordatorio) y la deja PENDING para que el
     * próximo `syncNow()` la suba. Si ya existe, la devuelve tal cual.
     *
     * Llamado desde `SyncTrigger` apenas la sesión pasa a SignedIn (ver
     * SyncTrigger.kt) — así toda cuenta nueva o existente termina con un
     * perfil local sin necesidad de una pantalla de onboarding dedicada
     * (no hay una definida en el documento, ver nota en User.kt).
     */
    suspend fun ensureProfile(): User

    suspend fun update(user: User)
}
