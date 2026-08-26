package com.seebudget.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espeja la tabla `planned_item` de Supabase (snake_case, ver
 * supabase/migrations/0005_budget_planned_item.sql +
 * 0006_planned_item_name_and_dates.sql). Separado del modelo de dominio
 * `PlannedItem` a propósito. `type`/`frequency`/`day_of_week` viajan como
 * String (mismo criterio que `status` en BudgetDto) — la conversión
 * enum↔String la hace SyncManager.kt.
 */
@Serializable
data class PlannedItemDto(
    val id: String,
    @SerialName("budget_id") val budgetId: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    // Fase 7 (RF-10) — ver KDoc de PlannedItem.kt.
    @SerialName("category_id") val categoryId: String? = null,
    val type: String,
    val amount: Long,
    val currency: String,
    val frequency: String,
    @SerialName("billing_day") val billingDay: Int? = null,
    @SerialName("specific_date") val specificDate: LocalDate? = null,
    @SerialName("day_of_week") val dayOfWeek: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)
