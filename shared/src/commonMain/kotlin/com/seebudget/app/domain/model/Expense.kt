package com.seebudget.app.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * RF-01 — Tabla única de gastos, también usada por el módulo de
 * presupuestos (`budgetId`/`plannedItemId` nullable — ver sección 7 del
 * documento, "Notas de diseño del modelo"): un gasto es un gasto, sin
 * importar desde qué pantalla se originó.
 *
 * Nota sobre IDs: uso `String` (UUID v4), no `Long` autoincremental. La app
 * es offline-first con sync multi-dispositivo (RNF-02/RF-07): un ID
 * autoincremental generado localmente en dos dispositivos distintos podría
 * colisionar al sincronizar; un UUID generado al crear el registro no.
 * La generación del UUID se hace en la capa de Repository.
 *
 * `userId` (no-nulo): dueño del gasto — necesario para que las políticas
 * RLS de Supabase y el sync multi-dispositivo (RF-07) tengan sentido. Ver
 * supabase/migrations/0001_init_category_expense_rls.sql.
 *
 * `deletedAt` (Fase 2, SyncManager): tombstone local — "borrar" un gasto
 * marca esta columna en vez de eliminar la fila (RNF-02 exige que Delete
 * también funcione offline-first; sin tombstone, un borrado hecho sin
 * conexión podía "resucitar" al sincronizar). El SyncManager hace el
 * DELETE real contra Supabase y recién ahí limpia la fila local.
 *
 * `type` (Fase 7, agregado en el chat): resuelve el gap documentado en
 * `ProjectionEngine` ("Expense siempre es un egreso — todavía no existe
 * forma de registrar ingreso real confirmado"). Reusa `PlannedItemType`
 * (el mismo enum de `PlannedItem`, no uno nuevo) en vez de crear una
 * tabla separada para ingresos confirmados — decisión tomada en el chat,
 * siguiendo la convención ya establecida del proyecto de "tabla única
 * Expense en vez de tablas duplicadas". Default `EXPENSE` para que las
 * filas creadas antes de Fase 7 (y el flujo genérico de "Registrar
 * gasto", RF-01, que no cambia) sigan significando lo mismo que siempre.
 * Confirmado en el chat: las filas `type = INCOME` (originadas en el
 * Check-in diario, RF-10, al confirmar un `PlannedItem` de tipo ingreso)
 * SÍ aparecen en el Listado de gastos general (pantalla 3) y en los
 * totales de Reportes (RF-03), unificado con el resto — no quedan
 * ocultas ni separadas en un contexto aparte.
 */
@Serializable
data class Expense(
    val id: String,
    val userId: String,
    val amount: Long, // unidades menores de `currency` — ver MoneyFormat. Siempre positivo — el signo lo da `type`, no `amount`.
    val currency: String, // código ISO 4217
    val categoryId: String,
    val date: LocalDate,
    val note: String?,
    val receiptImageUrl: String?,
    // El documento no define un set cerrado de métodos de pago todavía
    // (RF-01 solo dice "opcional"), así que queda como texto libre por
    // ahora. Si en algún momento lo querés como opciones fijas (ej.
    // Efectivo/Tarjeta/Transferencia), lo convertimos a enum.
    val paymentMethod: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
    val budgetId: String?, // null si el gasto no pertenece a ningún presupuesto
    val plannedItemId: String?, // null si es un gasto adicional no planificado
    val isPlannedOverride: Boolean, // true si el monto difiere del PlannedItem original ese día
    val deletedAt: Instant?,
    // Fase 7 — ver KDoc de la clase. Default EXPENSE: mantiene el
    // significado de todo el modelo/UI existente (RF-01) sin cambios.
    val type: PlannedItemType = PlannedItemType.EXPENSE,
)

/**
 * Estado de sincronización offline-first (RNF-02/RF-07): PENDING = falta
 * subir/confirmar contra Supabase; SYNCED = coincide con el servidor;
 * ERROR = el último intento de sync falló (queda PENDING hasta el
 * próximo intento — este valor lo deja definido el SyncManager para
 * diagnóstico, no se usa todavía para reintentos diferenciados).
 */
enum class SyncStatus { PENDING, SYNCED, ERROR }
