package com.seebudget.app.domain.model

/**
 * Apariencia de la app (agregado en el chat, RNF-07 — tema claro/oscuro).
 *
 * `SYSTEM` sigue el modo del teléfono (`isSystemInDarkTheme()`, ver
 * SeeBudgetTheme.kt) — es el default de una instalación nueva, mismo
 * comportamiento que había antes de que existiera esta preferencia.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Idioma de la UI (agregado en el chat, RNF-07 — selector de idioma).
 * Mismo patrón que `ThemeMode`: `SYSTEM` sigue el idioma del dispositivo
 * -- si Compose Resources no tiene una carpeta calificada para ese
 * idioma (hoy solo existe `values-en`), cae al default (`values`,
 * español) -- mismo comportamiento que tenía la app antes de que
 * existiera esta preferencia. Es el default de una instalación nueva.
 * Ver `LocalAppLocale.kt` (presentation/locale) para el mecanismo real
 * de cómo esto fuerza el idioma de `stringResource()`.
 */
enum class AppLanguage { ES, EN, SYSTEM }

/**
 * Preferencias locales del dispositivo — decisión explícita tomada en el
 * chat de que esto NO se sincroniza vía Supabase/UserSettings (a
 * diferencia de `User`, ver User.kt): cambiar de dispositivo no debería
 * "traer" un tema, y agregarlo a UserSettings hubiera requerido una
 * migración de Postgres que el usuario tendría que aplicar en su propia
 * base de datos en producción. Por eso vive en su propia tabla
 * (AppPreferences.sq), sin `createdAt`/`updatedAt`/`syncStatus` y sin
 * pasar por SyncManager.
 *
 * Fila única por dispositivo: siempre se lee/escribe con `id = SINGLETON_ID`.
 */
data class AppPreferences(
    val id: String = SINGLETON_ID,
    val themeMode: ThemeMode,
    val language: AppLanguage,
) {
    companion object {
        const val SINGLETON_ID = "app_preferences"
    }
}
