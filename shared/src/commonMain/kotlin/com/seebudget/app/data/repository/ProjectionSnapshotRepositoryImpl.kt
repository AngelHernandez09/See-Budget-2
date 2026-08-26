package com.seebudget.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.model.ProjectionSnapshot
import com.seebudget.app.domain.model.ProjectionSnapshotItem
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.ProjectionSnapshotRepository
import com.seebudget.app.domain.util.newId
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class ProjectionSnapshotRepositoryImpl(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
) : ProjectionSnapshotRepository {
    private val queries = db.projectionSnapshotQueries

    override fun observeByBudget(budgetId: String): Flow<List<ProjectionSnapshot>> =
        queries.selectByBudget(budgetId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getLatest(budgetId: String): ProjectionSnapshot? =
        queries.selectLatestByBudget(budgetId).executeAsOneOrNull()?.toDomain()

    override suspend fun getById(id: String): ProjectionSnapshot? =
        queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun create(
        budgetId: String,
        label: String,
        frozenInitialBalance: Long,
        frozenStartDate: LocalDate,
        frozenBaseCurrency: String,
        frozenPlannedItems: List<ProjectionSnapshotItem>,
    ): ProjectionSnapshot {
        val userId = authRepository.currentUserId()
            ?: error("No se puede crear una línea de referencia sin sesión iniciada")
        val now = Clock.System.now()
        val snapshot = ProjectionSnapshot(
            id = newId(),
            budgetId = budgetId,
            userId = userId,
            label = label,
            frozenInitialBalance = frozenInitialBalance,
            frozenStartDate = frozenStartDate,
            frozenBaseCurrency = frozenBaseCurrency,
            frozenPlannedItems = frozenPlannedItems,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            deletedAt = null,
        )
        queries.insert(
            id = snapshot.id,
            budgetId = snapshot.budgetId,
            userId = snapshot.userId,
            label = snapshot.label,
            frozenInitialBalance = snapshot.frozenInitialBalance,
            frozenStartDate = snapshot.frozenStartDate,
            frozenBaseCurrency = snapshot.frozenBaseCurrency,
            frozenPlannedItems = snapshot.frozenPlannedItems,
            createdAt = snapshot.createdAt,
            updatedAt = snapshot.updatedAt,
            syncStatus = snapshot.syncStatus,
            deletedAt = snapshot.deletedAt,
        )
        return snapshot
    }

    /** "Corrección de error" (RF-09): pisa label/saldo/fecha/plantilla congelados. */
    override suspend fun update(snapshot: ProjectionSnapshot) {
        queries.update(
            label = snapshot.label,
            frozenInitialBalance = snapshot.frozenInitialBalance,
            frozenStartDate = snapshot.frozenStartDate,
            frozenBaseCurrency = snapshot.frozenBaseCurrency,
            frozenPlannedItems = snapshot.frozenPlannedItems,
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
            id = snapshot.id,
        )
    }

    /** Tombstone, no borrado físico — ver comentario en ProjectionSnapshot.sq. */
    override suspend fun delete(id: String) {
        queries.softDelete(
            deletedAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            id = id,
        )
    }
}
