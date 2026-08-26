package com.seebudget.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espeja la tabla `user_settings` de Supabase (snake_case, ver
 * supabase/migrations/0004_user_settings.sql). Igual que
 * CategoryDto/ExpenseDto: sin `syncStatus` (bookkeeping puramente local,
 * ver SyncManager.kt) y sin `deletedAt` (no hay tombstone acá — ver
 * User.kt, no existe una funcionalidad de "borrar mi perfil" todavía).
 */
@Serializable
data class UserSettingsDto(
    val id: String,
    val email: String,
    @SerialName("base_currency") val baseCurrency: String,
    val locale: String,
    @SerialName("daily_reminder_time") val dailyReminderTime: LocalTime?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)
