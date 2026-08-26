package com.seebudget.app.data.repository

import com.seebudget.app.db.AppDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.repository.ActiveStatusConflict
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.util.newId
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class BudgetRepositoryImpl(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
) : BudgetRepository {
    private val queries = db.budgetQueries

    override fun observeAll(): Flow<List<Budget>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): Budget? =
        queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override fun observeActive(): Flow<Budget?> =
        queries.selectActive()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.toDomain() }

    /**
     * Auto-activar si no hay otro presupuesto ACTIVE (decisión tomada en
     * el chat): evita un paso extra para el caso más común (usuario sin
     * presupuesto que crea el primero). Si ya hay uno activo, el nuevo
     * nace PAUSED — no se puede violar la regla de uno solo activo.
     */
    override suspend fun create(
        name: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        initialBalance: Long,
        baseCurrency: String,
        notificationTime: LocalTime?,
    ): Budget {
        val userId = authRepository.currentUserId()
            ?: error("No se puede crear un presupuesto sin sesión iniciada")
        val now = Clock.System.now()
        val hasActive = queries.selectActive().executeAsOneOrNull() != null
        val budget = Budget(
            id = newId(),
            userId = userId,
            name = name,
            startDate = startDate,
            endDate = endDate,
            status = if (hasActive) BudgetStatus.PAUSED else BudgetStatus.ACTIVE,
            initialBalance = initialBalance,
            baseCurrency = baseCurrency,
            notificationTime = notificationTime,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            deletedAt = null,
        )
        queries.insert(
            id = budget.id,
            userId = budget.userId,
            name = budget.name,
            startDate = budget.startDate,
            endDate = budget.endDate,
            status = budget.status,
            initialBalance = budget.initialBalance,
            baseCurrency = budget.baseCurrency,
            notificationTime = budget.notificationTime,
            createdAt = budget.createdAt,
            updatedAt = budget.updatedAt,
            syncStatus = budget.syncStatus,
            deletedAt = budget.deletedAt,
        )
        return budget
    }

    override suspend fun update(budget: Budget) {
        queries.update(
            name = budget.name,
            startDate = budget.startDate,
            endDate = budget.endDate,
            initialBalance = budget.initialBalance,
            baseCurrency = budget.baseCurrency,
            notificationTime = budget.notificationTime,
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
            id = budget.id,
        )
    }

    /**
     * Pausa el ACTIVE actual (si hay uno distinto de [id]) y activa [id],
     * en una sola transacción — nunca deja pasar por un estado
     * intermedio con dos presupuestos ACTIVE ni con ninguno.
     */
    override suspend fun activate(id: String) {
        db.transaction {
            val now = Clock.System.now()
            val currentActive = queries.selectActive().executeAsOneOrNull()
            if (currentActive != null && currentActive.id != id) {
                queries.updateStatus(
                    status = BudgetStatus.PAUSED,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    id = currentActive.id,
                )
            }
            queries.updateStatus(
                status = BudgetStatus.ACTIVE,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
                id = id,
            )
        }
    }

    /**
     * Pausa manual (RF-08, agregado en el chat — ver KDoc de la
     * interfaz): solo tiene efecto si [id] está ACTIVE ahora mismo, para
     * no reactivar por accidente un presupuesto COMPLETED con un click
     * de UI desactualizado.
     */
    override suspend fun pause(id: String) {
        val current = queries.selectById(id).executeAsOneOrNull() ?: return
        if (current.status != BudgetStatus.ACTIVE) return
        queries.updateStatus(
            status = BudgetStatus.PAUSED,
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
            id = id,
        )
    }

    override suspend fun complete(id: String) {
        queries.updateStatus(
            status = BudgetStatus.COMPLETED,
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
            id = id,
        )
    }

    /**
     * Tombstone del presupuesto + cascada a sus PlannedItem y a sus
     * ProjectionSnapshot (Fase 6 — mismo criterio ya usado con
     * PlannedItem: si se borra el presupuesto, sus líneas de referencia
     * dejan de tener sentido). Los Expense ya vinculados (budgetId) NO se
     * tocan — se preservan tal cual, conservando el budgetId aunque
     * termine apuntando a un presupuesto eliminado.
     */
    override suspend fun delete(id: String) {
        db.transaction {
            val now = Clock.System.now()
            val itemIds = db.plannedItemQueries.selectActiveIdsByBudget(id).executeAsList()
            for (itemId in itemIds) {
                db.plannedItemQueries.softDelete(deletedAt = now, updatedAt = now, id = itemId)
            }
            val snapshotIds = db.projectionSnapshotQueries.selectActiveIdsByBudget(id).executeAsList()
            for (snapshotId in snapshotIds) {
                db.projectionSnapshotQueries.softDelete(deletedAt = now, updatedAt = now, id = snapshotId)
            }
            queries.softDelete(deletedAt = now, updatedAt = now, id = id)
        }
    }

    /**
     * Auto-completar por endDate (RF-08: "Al llegar la endDate, el
     * presupuesto pasa automáticamente a completed"). Se interpreta
     * "llegar" como que la endDate sigue siendo un día válido del
     * presupuesto — recién pasa a COMPLETED el día siguiente
     * (endDate < today), no el mismo día de endDate.
     *
     * Llamado por SyncManager al principio de cada ciclo de sync (no hay
     * cron/background job en esta app) — cualquier cambio de status que
     * dispare esto queda PENDING y se sube en el mismo ciclo.
     */
    override suspend fun normalizeLifecycle(today: LocalDate) {
        val now = Clock.System.now()
        val candidates = queries.selectNotCompletedWithEndDate().executeAsList()
        for (row in candidates) {
            val endDate = row.endDate ?: continue
            if (endDate < today) {
                queries.updateStatus(
                    status = BudgetStatus.COMPLETED,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    id = row.id,
                )
            }
        }
    }

    /**
     * Corrige el gap multi-dispositivo (agregado en el chat) — ver KDoc
     * de la interfaz. Se queda con el `updatedAt` más reciente entre los
     * ACTIVE encontrados y pausa el resto.
     */
    override suspend fun normalizeActiveStatus(): ActiveStatusConflict? {
        val actives = queries.selectAllActive().executeAsList()
        if (actives.size <= 1) return null
        val winner = actives.maxBy { it.updatedAt }
        val losers = actives.filter { it.id != winner.id }
        val now = Clock.System.now()
        for (loser in losers) {
            queries.updateStatus(
                status = BudgetStatus.PAUSED,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
                id = loser.id,
            )
        }
        return ActiveStatusConflict(
            keptActiveName = winner.name,
            pausedNames = losers.map { it.name },
        )
    }
}
