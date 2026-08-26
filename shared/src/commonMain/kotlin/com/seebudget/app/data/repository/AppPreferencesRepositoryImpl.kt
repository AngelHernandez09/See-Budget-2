package com.seebudget.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.model.AppPreferences
import com.seebudget.app.domain.model.AppLanguage
import com.seebudget.app.domain.model.ThemeMode
import com.seebudget.app.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Nota (mismo comentario que en Adapters.kt para Expense/Category): la
 * fila generada por SQLDelight para esta tabla también se llama
 * `AppPreferences` (com.seebudget.app.db.AppPreferences) — mismo nombre
 * que el modelo de dominio importado acá (com.seebudget.app.domain.model.
 * AppPreferences), paquetes distintos. Por eso este archivo nunca importa
 * la clase de `db` por su nombre simple: el tipo de fila queda inferido
 * (`it` en el `map`), sin necesitar la importación.
 */
class AppPreferencesRepositoryImpl(
    private val db: AppDatabase,
) : AppPreferencesRepository {
    private val queries = db.appPreferencesQueries

    override fun observeThemeMode(): Flow<ThemeMode> =
        queries.selectSingleton(AppPreferences.SINGLETON_ID)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.themeMode ?: ThemeMode.SYSTEM }

    override suspend fun ensureDefault() {
        queries.insertDefaultIfMissing(
            id = AppPreferences.SINGLETON_ID,
            themeMode = ThemeMode.SYSTEM,
            language = AppLanguage.SYSTEM,
        )
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        queries.updateThemeMode(themeMode = themeMode, id = AppPreferences.SINGLETON_ID)
    }

    override fun observeLanguage(): Flow<AppLanguage> =
        queries.selectSingleton(AppPreferences.SINGLETON_ID)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.language ?: AppLanguage.SYSTEM }

    override suspend fun setLanguage(language: AppLanguage) {
        queries.updateLanguage(language = language, id = AppPreferences.SINGLETON_ID)
    }
}
