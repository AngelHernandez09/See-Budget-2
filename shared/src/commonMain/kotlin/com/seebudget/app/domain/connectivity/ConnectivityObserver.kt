package com.seebudget.app.domain.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Puente hacia el estado de conectividad de cada plataforma — mismo
 * criterio que `ReminderScheduler`/`FileExporter` (Fase 8): la interfaz
 * vive en `commonMain`, la implementación real se registra por
 * plataforma en `platformModule()`. "Solo Android" para esta primera
 * pasada (decidido en el chat, ver KDoc de `SyncTrigger`): Android
 * registra `AndroidConnectivityObserver` (ver androidMain, vía
 * `ConnectivityManager.NetworkCallback` — push real del sistema
 * operativo, sin polling); iOS/Desktop/Web registran
 * [NoOpConnectivityObserver] y siguen dependiendo únicamente del
 * polling periódico de `SyncTrigger`, igual que antes de este cambio.
 */
interface ConnectivityObserver {
    /**
     * Emite el estado actual al suscribirse, y cada vez que cambia
     * después. `true` = hay una red con salida a Internet validada por
     * el sistema operativo (no garantiza que Supabase en particular sea
     * alcanzable — eso lo maneja el propio `try/catch` de
     * `SyncManager.syncNow()`).
     */
    fun observe(): Flow<Boolean>
}

/**
 * "Solo Android" para esta primera pasada (ver KDoc de la interfaz):
 * siempre `true` y nunca cambia, así que nunca dispara ningún resync
 * extra — `SyncTrigger` sigue dependiendo únicamente de su polling
 * periódico en estas plataformas, sin ningún cambio de comportamiento.
 */
class NoOpConnectivityObserver : ConnectivityObserver {
    override fun observe(): Flow<Boolean> = flowOf(true)
}
