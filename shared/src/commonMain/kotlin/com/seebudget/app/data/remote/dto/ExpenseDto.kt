package com.seebudget.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espeja la tabla `expense` de Supabase (snake_case, ver
 * supabase/migrations/0001_init_category_expense_rls.sql +
 * 0008_expense_type.sql). Separado del modelo de dominio `Expense` a
 * propósito. Sin `syncStatus`/`deletedAt`: son bookkeeping puramente
 * local (ver SyncManager.kt) — un borrado se sincroniza con un DELETE
 * real, no con un campo acá.
 *
 * `type` (Fase 7): viaja como String (mismo criterio que `type` en
 * `PlannedItemDto`) — la conversión enum↔String la hace SyncManager.kt.
 * Sin default: a diferencia de la columna remota (que sí tiene DEFAULT
 * 'EXPENSE' para las filas ya existentes), acá el default vive del lado
 * de Kotlin en `ExpenseEntity.toDto()`, no en el DTO.
 */
@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val amount: Long,
    val currency: String,
    @SerialName("category_id") val categoryId: String,
    val date: LocalDate,
    val note: String? = null,
    @SerialName("receipt_image_url") val receiptImageUrl: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("budget_id") val budgetId: String? = null,
    @SerialName("planned_item_id") val plannedItemId: String? = null,
    @SerialName("is_planned_override") val isPlannedOverride: Boolean,
    val type: String = "EXPENSE",
)
