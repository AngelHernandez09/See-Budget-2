package com.seebudget.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espeja la tabla `category` de Supabase (snake_case, ver
 * supabase/migrations/0001_init_category_expense_rls.sql y
 * 0002_category_timestamps.sql). Separado del modelo de dominio
 * `Category` a propósito — el dominio no debería saber de convenciones de
 * nombres de columnas remotas. Sin `syncStatus`/`deletedAt`: son
 * bookkeeping puramente local (ver SyncManager.kt).
 */
@Serializable
data class CategoryDto(
    val id: String,
    @SerialName("user_id") val userId: String?,
    val name: String,
    val icon: String,
    val color: String,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)
