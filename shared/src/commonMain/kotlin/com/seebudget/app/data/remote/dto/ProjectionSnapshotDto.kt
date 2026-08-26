package com.seebudget.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espeja la tabla `projection_snapshot` de Supabase (snake_case, ver
 * supabase/migrations/0007_projection_snapshot.sql). `frozen_planned_items`
 * es `jsonb` del lado de Postgres — acá viaja tipado como lista anidada de
 * `ProjectionSnapshotItemDto` (kotlinx.serialization/postgrest-kt lo
 * serializan como JSON anidado solos, no hace falta manejarlo como texto
 * crudo).
 */
@Serializable
data class ProjectionSnapshotDto(
    val id: String,
    @SerialName("budget_id") val budgetId: String,
    @SerialName("user_id") val userId: String,
    val label: String,
    @SerialName("frozen_initial_balance") val frozenInitialBalance: Long,
    @SerialName("frozen_start_date") val frozenStartDate: LocalDate,
    @SerialName("frozen_base_currency") val frozenBaseCurrency: String,
    @SerialName("frozen_planned_items") val frozenPlannedItems: List<ProjectionSnapshotItemDto>,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)

/**
 * Entrada congelada dentro de `frozen_planned_items` — mismo criterio que
 * `PlannedItemDto` para `type`/`frequency`/`day_of_week` (viajan como
 * String, la conversión enum↔String la hace SyncManager.kt).
 * `plannedItemId` referencia al `PlannedItem` original solo para diffs de
 * "Historial de proyecciones" — no es una FK real, ver ProjectionSnapshot.kt.
 */
@Serializable
data class ProjectionSnapshotItemDto(
    @SerialName("planned_item_id") val plannedItemId: String,
    val name: String,
    val type: String,
    val amount: Long,
    val currency: String,
    val frequency: String,
    @SerialName("billing_day") val billingDay: Int? = null,
    @SerialName("specific_date") val specificDate: LocalDate? = null,
    @SerialName("day_of_week") val dayOfWeek: String? = null,
    @SerialName("is_active") val isActive: Boolean,
)
