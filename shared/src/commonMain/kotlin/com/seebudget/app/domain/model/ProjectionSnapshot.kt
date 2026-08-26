package com.seebudget.app.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * RF-09 — Una "línea de referencia" congelada del `ProjectionEngine`
 * (Fase 6): la sección 7 del documento la describe como "id, budgetId,
 * createdAt, label, plantilla de PlannedItem congelada en ese momento".
 *
 * Además de la plantilla de `PlannedItem`, este snapshot también congela
 * `frozenInitialBalance`/`frozenStartDate`/`frozenBaseCurrency` del
 * `Budget` al momento de generarse — decisión confirmada en el chat: si
 * más adelante el usuario edita el saldo inicial o la fecha de inicio
 * desde "Ajustes del presupuesto", las líneas de referencia YA generadas
 * no se mueven solas. Ese tipo de edición (igual que editar la plantilla
 * de `PlannedItem`) dispara el mismo diálogo "¿corrección de error o
 * cambio real?" de RF-09:
 * - Corrección de error: se llama a `update` (pisa este mismo snapshot,
 *   sin generar uno nuevo) — la línea de referencia "siempre fue" el
 *   valor corregido.
 * - Cambio real: el usuario elige mantener la línea actual tal cual
 *   (no se toca nada acá) o generar una nueva línea de referencia desde
 *   hoy (`create` de un snapshot nuevo, con los valores vigentes).
 *
 * La única excepción a ese mecanismo es el cambio de `baseCurrency` del
 * presupuesto en curso (sección 12 del documento): ese caso SIEMPRE
 * convierte los snapshots existentes con la tasa vigente al momento del
 * cambio, sin pasar por el diálogo corrección/cambio real — todavía no
 * implementado, queda para cuando se aborde esa feature específica.
 *
 * `frozenPlannedItems` se persiste como JSON (ver
 * `ProjectionSnapshotItemsAdapter` en Adapters.kt) — es una copia
 * congelada, no una FK viva a `PlannedItem`: si el `PlannedItem` original
 * se edita o se borra después, este snapshot no cambia. Por eso cada
 * entrada guarda su propia copia de los campos relevantes en vez de solo
 * un id.
 *
 * `userId`/`createdAt`/`updatedAt`/`syncStatus`/`deletedAt`: mismo
 * mecanismo de sync offline-first que Budget/PlannedItem.
 */
@Serializable
data class ProjectionSnapshot(
    val id: String,
    val budgetId: String,
    val userId: String,
    val label: String, // ej. "Original", "Ajuste 15 enero"
    val frozenInitialBalance: Long, // unidades menores de `frozenBaseCurrency`
    val frozenStartDate: LocalDate,
    val frozenBaseCurrency: String,
    val frozenPlannedItems: List<ProjectionSnapshotItem>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
    val deletedAt: Instant?,
)

/**
 * Copia congelada de los campos de un `PlannedItem` relevantes para el
 * `ProjectionEngine`, tal como estaban al generarse el snapshot.
 * `plannedItemId` referencia al `PlannedItem` original solo para poder
 * mostrar "qué cambió respecto al snapshot anterior" (sección 8 del
 * documento) — no es una FK real, el ítem original puede ya no existir.
 */
@Serializable
data class ProjectionSnapshotItem(
    val plannedItemId: String,
    val name: String,
    val type: PlannedItemType,
    val amount: Long,
    val currency: String,
    val frequency: PlannedItemFrequency,
    val billingDay: Int?,
    val specificDate: LocalDate?,
    val dayOfWeek: DayOfWeek?,
    val isActive: Boolean,
)
