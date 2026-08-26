package com.seebudget.app.data.sync

import com.seebudget.app.domain.connectivity.ConnectivityObserver
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.AuthSessionState
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Política de "¿cuándo sincronizo?" (acordada en el chat: automático al
 * abrir la app + al reconectar). Pensado para correr desde un
 * `LaunchedEffect(Unit)` en App.kt, que lo cancela solo al cerrar la app
 * — `run()` no retorna mientras el llamador lo mantenga vivo.
 *
 * **Arquitectura (agregado en el chat):** "al reconectar" ya no es solo
 * una aproximación por polling. Mientras hay sesión, corren dos cosas en
 * paralelo:
 * - El loop periódico de siempre: `syncNow()` cada [SYNC_INTERVAL_MS].
 *   Se mantiene en todas las plataformas como red de seguridad (cubre,
 *   por ejemplo, que el sistema operativo reporte "hay red" pero
 *   Supabase en particular no responda todavía, y es lo único que
 *   corre en las plataformas sin listener real — ver abajo).
 * - Un resync inmediato apenas [ConnectivityObserver] reporta una
 *   transición real de "sin conexión" a "con conexión". "Solo Android"
 *   por ahora (decidido en el chat, ver KDoc de `ConnectivityObserver`/
 *   `AndroidConnectivityObserver`): en las demás plataformas,
 *   `NoOpConnectivityObserver` nunca reporta un cambio, así que esa
 *   segunda parte no hace nada — se comportan exactamente igual que
 *   antes de este cambio, solo con el loop periódico.
 *
 * Fase 3: apenas la sesión pasa a SignedIn, se asegura de que exista el
 * perfil local (`UserSettingsRepository.ensureProfile()`, ver User.kt)
 * antes de arrancar el loop de sync — así toda cuenta (nueva o existente
 * en otro dispositivo) termina con una fila de `user_settings` sin
 * necesidad de una pantalla de onboarding dedicada. `ensureProfile()` solo
 * toca la base local (no hace red), así que no hace falta un try/catch acá
 * — si falla, es un bug real, no un problema de conectividad.
 */
class SyncTrigger(
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val connectivityObserver: ConnectivityObserver,
) {
    /** Passthrough de `SyncManager.events` — así App.kt no necesita inyectar `SyncManager` aparte solo para esto. */
    val events: SharedFlow<SyncEvent> = syncManager.events

    /** Passthrough de `SyncManager.syncState` (agregado en el chat — Ajustes, sección Sincronización) — mismo motivo que `events`. */
    val syncState: StateFlow<SyncCycleState> = syncManager.syncState

    /** Disparo manual (botón "Sincronizar ahora" en Ajustes) — mismo `syncNow()` que ya corre solo, sin pasar por `ensureProfile()` de nuevo (ya corrió al iniciar sesión, ver `run()`). */
    suspend fun syncNow() = syncManager.syncNow()

    suspend fun run() {
        authRepository.observeSession().collectLatest { session ->
            if (session is AuthSessionState.SignedIn) {
                userSettingsRepository.ensureProfile()
                coroutineScope {
                    launch {
                        while (true) {
                            syncManager.syncNow()
                            delay(SYNC_INTERVAL_MS)
                        }
                    }
                    launch {
                        // drop(1): el primer valor es el estado de
                        // arranque, no una "reconexión" — solo interesa
                        // la transición false -> true que llegue después.
                        connectivityObserver.observe()
                            .distinctUntilChanged()
                            .drop(1)
                            .filter { isConnected -> isConnected }
                            .collect { syncManager.syncNow() }
                    }
                }
            }
            // SignedOut/Loading: no hay nada que sincronizar. collectLatest
            // cancela todo lo de arriba (loop + listener) solo si el
            // estado cambia.
        }
    }

    companion object {
        private const val SYNC_INTERVAL_MS = 30_000L
    }
}
