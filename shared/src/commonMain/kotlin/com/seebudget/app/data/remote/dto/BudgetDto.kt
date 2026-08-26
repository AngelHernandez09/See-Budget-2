package com.seebudget.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espeja la tabla `budget` de Supabase (snake_case, ver
 * supabase/migrations/0005_budget_planned_item.sql). Separado del modelo
 * de dominio `Budget` a propósito — el dominio no debería saber de
 * convenciones de nombres de columnas remotas. Sin `syncStatus`/
 * `deletedAt`: son bookkeeping puramente local (ver SyncManager.kt).
 *
 * `status` viaja como String (no como el enum `BudgetStatus` de dominio)
 * — mismo criterio que el resto de los DTOs, que no conocen tipos de
 * dominio; la conversión enum↔String la hace SyncManager.kt.
 */
@Serializable
data class BudgetDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("start_date") val startDate: LocalDate,
    @SerialName("end_date") val endDate: LocalDate? = null,
    val status: String,
    @SerialName("initial_balance") val initialBalance: Long,
    @SerialName("base_currency") val baseCurrency: String,
    @SerialName("notification_time") val notificationTime: LocalTime? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)
