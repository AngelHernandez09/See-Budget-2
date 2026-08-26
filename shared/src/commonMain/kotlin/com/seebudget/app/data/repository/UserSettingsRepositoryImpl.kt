package com.seebudget.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.model.User
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserSettingsRepositoryImpl(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
) : UserSettingsRepository {
    private val queries = db.userSettingsQueries

    override fun observe(userId: String): Flow<User?> =
        queries.selectById(userId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override suspend fun ensureProfile(): User {
        val userId = authRepository.currentUserId()
            ?: error("No se puede crear el perfil sin sesión iniciada")
        queries.selectById(userId).executeAsOneOrNull()?.let { return it.toDomain() }

        val now = Clock.System.now()
        val user = User(
            id = userId,
            email = authRepository.currentUserEmail().orEmpty(),
            // Defaults razonables (no hay onboarding definido todavía para
            // elegirlos antes de esto, ver User.kt) — editables en
            // cualquier momento desde Ajustes generales (RF-04, pantalla 11).
            baseCurrency = "USD",
            locale = "es",
            dailyReminderTime = null,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        queries.insert(
            id = user.id,
            email = user.email,
            baseCurrency = user.baseCurrency,
            locale = user.locale,
            dailyReminderTime = user.dailyReminderTime,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            syncStatus = user.syncStatus,
        )
        return user
    }

    override suspend fun update(user: User) {
        queries.update(
            baseCurrency = user.baseCurrency,
            locale = user.locale,
            dailyReminderTime = user.dailyReminderTime,
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
            id = user.id,
        )
    }
}
