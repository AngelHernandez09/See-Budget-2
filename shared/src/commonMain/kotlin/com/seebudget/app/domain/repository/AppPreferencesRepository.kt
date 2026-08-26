package com.seebudget.app.domain.repository

import com.seebudget.app.domain.model.AppLanguage
import com.seebudget.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Preferencia de apariencia (agregado en el chat, RNF-07 — tema claro/
 * oscuro). Local-only a propósito: ver KDoc completo de `AppPreferences`
 * (domain/model) sobre por qué esto no vive en `UserSettingsRepository`.
 */
interface AppPreferencesRepository {
    /** Nunca emite null: `ensureDefault()` corre antes de que la UI pueda observar esto — ver App.kt. */
    fun observeThemeMode(): Flow<ThemeMode>

    /** Idempotente — si ya existe la fila local, no hace nada. */
    suspend fun ensureDefault()

    suspend fun setThemeMode(themeMode: ThemeMode)

    /** Agregado en el chat, RNF-07 (selector de idioma) — mismo patrón que observeThemeMode(). Nunca emite null. */
    fun observeLanguage(): Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)
}
