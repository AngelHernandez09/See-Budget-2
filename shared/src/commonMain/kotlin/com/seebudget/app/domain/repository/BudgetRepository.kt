package com.seebudget.app.domain.repository

import com.seebudget.app.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * RF-08 — acceso y ciclo de vida de presupuestos. Sigue el mismo patrón
 * offline-first que Category/Expense (ver BudgetRepositoryImpl): siempre
 * lee/escribe primero en SQLDelight local, el SyncManager reconcilia con
 * Supabase.
 *
 * El repositorio controla `id`/`createdAt`/`updatedAt`/`syncStatus` igual
 * que ExpenseRepository — quien llama a `create`/`update` no los maneja
 * directo.
 *
 * Reglas de ciclo de vida (RF-08, decididas en el chat) que vive acá, no
 * en el ViewModel:
 * - Solo un presupuesto puede estar ACTIVE a la vez.
 * - `create()`: si no hay ningún ACTIVE en ese momento, el nuevo nace
 *   ACTIVE; si ya hay uno, nace PAUSED.
 * - `activate()`: pausa el ACTIVE actual (si hay uno distinto) y activa
 *   el indicado, en una sola transacción.
 * - `pause()` (agregado en el chat, notas de UI): pausa manualmente un
 *   presupuesto ACTIVE sin activar otro — antes la única forma de pasar
 *   a PAUSED era como efecto secundario de `activate()` sobre otro
 *   presupuesto, lo que dejaba sin salida al caso de un solo presupuesto
 *   que el usuario quiere pausar sin completarlo/eliminarlo. Puede dejar
 *   la app sin ningún presupuesto ACTIVE — mismo criterio que
 *   `complete()`/`delete()`, la app solo "sugiere" activar otro.
 * - `complete()`/`delete()` NO activan otro presupuesto automáticamente
 *   — el documento dice que la app solo "sugiere" (no obliga) elegir uno
 *   nuevo; la app puede quedar sin ningún presupuesto activo.
 * - `delete()` cascada tombstone a los `PlannedItem` del presupuesto; los
 *   `Expense` ya vinculados (`budgetId`) NO se tocan.
 * - `normalizeLifecycle()`: pasa a COMPLETED cualquier presupuesto no
 *   completado cuya `endDate` ya pasó. Pensado para que lo llame
 *   `SyncManager` en cada ciclo (no hay cron/background job en esta app),
 *   no para uso directo desde UI.
 * - `normalizeActiveStatus()` (agregado en el chat — gap multi-
 *   dispositivo): la invariante "solo un ACTIVE" la garantiza siempre
 *   `activate()` DENTRO de un mismo dispositivo (una sola transacción),
 *   pero `Budget` sincroniza con Supabase por LWW fila-por-fila (ver
 *   SyncManager.pullBudgets()), sin ningún chequeo cruzado entre filas.
 *   Si dos dispositivos activan presupuestos DISTINTOS mientras están
 *   offline entre sí, después de sincronizar ambos pueden terminar con
 *   más de un `Budget` en estado ACTIVE. `normalizeActiveStatus()`
 *   corrige eso: conserva ACTIVE solo el de `updatedAt` más reciente y
 *   pausa el resto (mismo criterio LWW que el resto del sync). Pensado
 *   para que lo llame `SyncManager` justo después de `pullBudgets()`, en
 *   cada ciclo — no para uso directo desde UI.
 */
interface BudgetRepository {
    fun observeAll(): Flow<List<Budget>>
    suspend fun getById(id: String): Budget?

    /** RF-05 (Fase 8) — el presupuesto ACTIVE actual (o null si no hay ninguno), reactivo. Usado por `ReminderTrigger` para saber cuándo reprogramar la notificación diaria. */
    fun observeActive(): Flow<Budget?>

    suspend fun create(
        name: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        initialBalance: Long,
        baseCurrency: String,
        notificationTime: LocalTime?,
    ): Budget

    /**
     * Actualiza los campos editables de un presupuesto existente
     * (nombre, fechas, saldo inicial, moneda, hora de notificación).
     * NO cambia `status` — eso pasa por `activate()`/`pause()`/`complete()`.
     */
    suspend fun update(budget: Budget)

    /** Pausa el ACTIVE actual (si hay uno distinto) y activa [id]. */
    suspend fun activate(id: String)

    /** Pausa manualmente [id] sin activar otro — no-op si [id] no está ACTIVE. Ver KDoc de la interfaz. */
    suspend fun pause(id: String)

    /** Marca [id] como COMPLETED (acción manual, "Completar presupuesto"). */
    suspend fun complete(id: String)

    /** Tombstone del presupuesto + cascada a sus PlannedItem. */
    suspend fun delete(id: String)

    /** Ver KDoc de la interfaz. Llamado por SyncManager, no por UI. */
    suspend fun normalizeLifecycle(today: LocalDate)

    /** Ver KDoc de la interfaz (gap multi-dispositivo). `null` si no había ningún conflicto que corregir. */
    suspend fun normalizeActiveStatus(): ActiveStatusConflict?
}

/**
 * Resultado de [BudgetRepository.normalizeActiveStatus] cuando sí hubo
 * que corregir algo — nombres (no ids) porque lo único que consume esto
 * hoy es un aviso de texto para el usuario (`SyncManager.events`).
 */
data class ActiveStatusConflict(
    val keptActiveName: String,
    val pausedNames: List<String>,
)
