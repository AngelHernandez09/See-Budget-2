package com.seebudget.app.data.sync

import com.seebudget.app.data.remote.dto.BudgetDto
import com.seebudget.app.data.remote.dto.CategoryDto
import com.seebudget.app.data.remote.dto.ExpenseDto
import com.seebudget.app.data.remote.dto.PlannedItemDto
import com.seebudget.app.data.remote.dto.ProjectionSnapshotDto
import com.seebudget.app.data.remote.dto.ProjectionSnapshotItemDto
import com.seebudget.app.data.remote.dto.UserSettingsDto
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.db.Budget as BudgetEntity
import com.seebudget.app.db.Category as CategoryEntity
import com.seebudget.app.db.Expense as ExpenseEntity
import com.seebudget.app.db.PlannedItem as PlannedItemEntity
import com.seebudget.app.db.ProjectionSnapshot as ProjectionSnapshotEntity
import com.seebudget.app.db.UserSettings as UserSettingsEntity
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.ProjectionSnapshotItem
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.BudgetRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Sincroniza `category`/`expense`/`user_settings`/`budget`/`planned_item`/
 * `projection_snapshot` entre SQLDelight local y Supabase.
 *
 * Decisiones acordadas en el chat:
 * - Conflictos: last-write-wins por `updatedAt`.
 * - Borrado offline-first (RNF-02): tombstone local (`deletedAt`, ver
 *   Category.sq/Expense.sq/Budget.sq/PlannedItem.sq/ProjectionSnapshot.sq)
 *   — Supabase no tiene `deleted_at` remoto; el borrado se sincroniza con
 *   un DELETE real contra la tabla, recién entonces se limpia la fila
 *   local (`hardDeleteSynced`). `user_settings` no tiene este mecanismo
 *   (sin tombstone, ver User.kt — no hay funcionalidad de "borrar mi
 *   perfil" todavía).
 * - Orden de un ciclo: push primero (lo pendiente local sube/borra en el
 *   servidor), pull después (trae lo que haya cambiado en otro
 *   dispositivo). `budget` se empuja antes que `planned_item`/
 *   `projection_snapshot` (FK real contra `budget.id`, ver migraciones
 *   0005/0007) — mismo criterio ya usado entre `category`/`expense`. Ver
 *   SyncTrigger.kt para cuándo se dispara `syncNow()`.
 * - Fase 5: antes de empujar nada, `normalizeLifecycle()` pasa a
 *   COMPLETED cualquier presupuesto cuya `endDate` ya pasó (RF-08) — no
 *   hay cron/background job en esta app, así que este es el único lugar
 *   donde se revisa esa transición automática. Cualquier cambio que
 *   dispare queda PENDING y se sube en el mismo ciclo, más abajo.
 * - Agregado en el chat (gap multi-dispositivo, ver KDoc de
 *   `BudgetRepository.normalizeActiveStatus`): justo después de
 *   `pullBudgets()`, se corrige si quedó más de un `Budget` ACTIVE. Si
 *   corrigió algo, se emite un [SyncEvent] por [events] para que la UI
 *   pueda avisarle al usuario — a diferencia de `normalizeLifecycle()`
 *   (silencioso, es una transición esperada del ciclo de vida), esto es
 *   una corrección de un conflicto que vale la pena mostrar.
 * - **Propagación de borrados (agregado en el chat — Arquitectura):**
 *   cada `pull*()` de una tabla con tombstone (category/budget/
 *   plannedItem/projectionSnapshot/expense) ahora también detecta el
 *   caso inverso al de `replaceFromRemote`: un id que existe localmente
 *   (de este usuario) pero ya no aparece en el pull remoto es porque
 *   otro dispositivo lo borró y ese borrado ya se confirmó en el
 *   servidor. Antes, ese id quedaba local hasta que algo lo pisara de
 *   nuevo (o para siempre, si nunca se volvía a tocar) — ver
 *   `propagateRemoteDeletes()` más abajo para el detalle completo.
 *   `user_settings` queda afuera a propósito: no existe todavía una
 *   funcionalidad de "borrar mi perfil/cuenta" (ver comentario más
 *   arriba), así que no hay ningún borrado real que propagar ahí.
 * - **Estado del ciclo de sync (agregado en el chat — Ajustes, sección
 *   Sincronización):** [syncState] expone Idle/Syncing/Success/Failure
 *   del último `syncNow()`, para que la UI pueda mostrar "última
 *   sincronización" y un botón "Sincronizar ahora". No persistido a
 *   propósito — vive en memoria mientras la app está abierta, mismo
 *   criterio "primer corte simple" que el resto de esta sección; se
 *   resetea a [SyncCycleState.Idle] en cada apertura de la app hasta que
 *   el primer `syncNow()` automático (ver `SyncTrigger`) corre, lo cual
 *   pasa casi al instante.
 *
 * Gap conocido, no resuelto en este primer corte: si el borrado y la
 * creación de una fila con el mismo id ocurren en la ventana entre el
 * pull de una tabla y el siguiente ciclo completo (raro, pero posible
 * con ids reciclados — no es el caso de esta app, que usa UUIDs), el
 * orden podría quedar mal resuelto. No se contempla porque no puede
 * pasar en la práctica con generación de ids por UUID.
 */
class SyncManager(
    private val db: AppDatabase,
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val budgetRepository: BudgetRepository,
) {
    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)

    /** Eventos "avisables" al usuario ocurridos durante un `syncNow()` — ver KDoc de la clase. No es un log completo del sync. */
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val _syncState = MutableStateFlow<SyncCycleState>(SyncCycleState.Idle)

    /** Ver KDoc de la clase ("Estado del ciclo de sync"). */
    val syncState: StateFlow<SyncCycleState> = _syncState.asStateFlow()

    suspend fun syncNow() {
        val currentUserId = authRepository.currentUserId() ?: return
        _syncState.value = SyncCycleState.Syncing
        try {
            budgetRepository.normalizeLifecycle(Clock.System.todayIn(TimeZone.currentSystemDefault()))
            pushCategories()
            pushBudgets()
            pushPlannedItems()
            pushProjectionSnapshots()
            pushExpenses()
            pushUserSettings()
            pullCategories(currentUserId)
            pullBudgets(currentUserId)
            val conflict = budgetRepository.normalizeActiveStatus()
            if (conflict != null) {
                _events.emit(
                    SyncEvent.ActiveBudgetConflictResolved(
                        keptActiveName = conflict.keptActiveName,
                        pausedNames = conflict.pausedNames,
                    )
                )
            }
            pullPlannedItems(currentUserId)
            pullProjectionSnapshots(currentUserId)
            pullExpenses(currentUserId)
            pullUserSettings()
            _syncState.value = SyncCycleState.Success(Clock.System.now())
        } catch (e: Exception) {
            // Antes esto se tragaba en silencio (motivo por el que el bug de
            // sync_status en `expense` pasó desapercibido: cada upsert
            // fallaba por una violación NOT NULL y nunca se veía en ningún
            // lado). Dejamos constancia en consola/Logcat aunque no haya
            // conexión — es un log, no debería reintentarse desde acá:
            // SyncTrigger ya reintenta en el próximo ciclo.
            println("[SyncManager] syncNow() falló: ${e::class.simpleName}: ${e.message}")
            _syncState.value = SyncCycleState.Failure(
                at = Clock.System.now(),
                message = e.message ?: (e::class.simpleName ?: "Error desconocido"),
            )
        }
    }

    private suspend fun pushCategories() {
        val pending = db.categoryQueries.selectAllForSync().executeAsList()
            .filter { it.syncStatus == SyncStatus.PENDING && it.userId != null }
        for (row in pending) {
            if (row.deletedAt != null) {
                supabaseClient.from("category").delete { filter { eq("id", row.id) } }
                db.categoryQueries.hardDeleteSynced(row.id)
            } else {
                supabaseClient.from("category").upsert(row.toDto())
                db.categoryQueries.markSynced(row.id)
            }
        }
    }

    private suspend fun pushExpenses() {
        val pending = db.expenseQueries.selectAllForSync().executeAsList()
            .filter { it.syncStatus == SyncStatus.PENDING }
        for (row in pending) {
            if (row.deletedAt != null) {
                supabaseClient.from("expense").delete { filter { eq("id", row.id) } }
                db.expenseQueries.hardDeleteSynced(row.id)
            } else {
                supabaseClient.from("expense").upsert(row.toDto())
                db.expenseQueries.markSynced(row.id)
            }
        }
    }

    private suspend fun pushUserSettings() {
        val pending = db.userSettingsQueries.selectAllForSync().executeAsList()
            .filter { it.syncStatus == SyncStatus.PENDING }
        for (row in pending) {
            supabaseClient.from("user_settings").upsert(row.toDto())
            db.userSettingsQueries.markSynced(row.id)
        }
    }

    private suspend fun pushBudgets() {
        val pending = db.budgetQueries.selectAllForSync().executeAsList()
            .filter { it.syncStatus == SyncStatus.PENDING }
        for (row in pending) {
            if (row.deletedAt != null) {
                supabaseClient.from("budget").delete { filter { eq("id", row.id) } }
                db.budgetQueries.hardDeleteSynced(row.id)
            } else {
                supabaseClient.from("budget").upsert(row.toDto())
                db.budgetQueries.markSynced(row.id)
            }
        }
    }

    private suspend fun pushPlannedItems() {
        val pending = db.plannedItemQueries.selectAllForSync().executeAsList()
            .filter { it.syncStatus == SyncStatus.PENDING }
        for (row in pending) {
            if (row.deletedAt != null) {
                supabaseClient.from("planned_item").delete { filter { eq("id", row.id) } }
                db.plannedItemQueries.hardDeleteSynced(row.id)
            } else {
                supabaseClient.from("planned_item").upsert(row.toDto())
                db.plannedItemQueries.markSynced(row.id)
            }
        }
    }

    private suspend fun pushProjectionSnapshots() {
        val pending = db.projectionSnapshotQueries.selectAllForSync().executeAsList()
            .filter { it.syncStatus == SyncStatus.PENDING }
        for (row in pending) {
            if (row.deletedAt != null) {
                supabaseClient.from("projection_snapshot").delete { filter { eq("id", row.id) } }
                db.projectionSnapshotQueries.hardDeleteSynced(row.id)
            } else {
                supabaseClient.from("projection_snapshot").upsert(row.toDto())
                db.projectionSnapshotQueries.markSynced(row.id)
            }
        }
    }

    private suspend fun pullCategories(currentUserId: String) {
        val remoteRows = supabaseClient.from("category").select().decodeList<CategoryDto>()
        val remoteIds = remoteRows.map { it.id }.toSet()
        val localRows = db.categoryQueries.selectAllForSync().executeAsList()
        val localById = localRows.associateBy { it.id }
        for (remote in remoteRows) {
            val local = localById[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                db.categoryQueries.replaceFromRemote(
                    id = remote.id,
                    userId = remote.userId,
                    name = remote.name,
                    icon = remote.icon,
                    color = remote.color,
                    isDefault = remote.isDefault,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                )
            }
        }
        propagateRemoteDeletes(
            localRows = localRows,
            remoteIds = remoteIds,
            currentUserId = currentUserId,
            id = { it.id },
            userId = { it.userId },
            syncStatus = { it.syncStatus },
            deleteLocalById = { db.categoryQueries.deleteById(it) },
        )
    }

    private suspend fun pullExpenses(currentUserId: String) {
        val remoteRows = supabaseClient.from("expense").select().decodeList<ExpenseDto>()
        val remoteIds = remoteRows.map { it.id }.toSet()
        val localRows = db.expenseQueries.selectAllForSync().executeAsList()
        val localById = localRows.associateBy { it.id }
        for (remote in remoteRows) {
            val local = localById[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                db.expenseQueries.replaceFromRemote(
                    id = remote.id,
                    userId = remote.userId,
                    amount = remote.amount,
                    currency = remote.currency,
                    categoryId = remote.categoryId,
                    date = remote.date,
                    note = remote.note,
                    receiptImageUrl = remote.receiptImageUrl,
                    paymentMethod = remote.paymentMethod,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    budgetId = remote.budgetId,
                    plannedItemId = remote.plannedItemId,
                    isPlannedOverride = remote.isPlannedOverride,
                    type = PlannedItemType.valueOf(remote.type),
                )
            }
        }
        propagateRemoteDeletes(
            localRows = localRows,
            remoteIds = remoteIds,
            currentUserId = currentUserId,
            id = { it.id },
            userId = { it.userId },
            syncStatus = { it.syncStatus },
            deleteLocalById = { db.expenseQueries.deleteById(it) },
        )
    }

    private suspend fun pullUserSettings() {
        val remoteRows = supabaseClient.from("user_settings").select().decodeList<UserSettingsDto>()
        val localById = db.userSettingsQueries.selectAllForSync().executeAsList().associateBy { it.id }
        for (remote in remoteRows) {
            val local = localById[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                db.userSettingsQueries.replaceFromRemote(
                    id = remote.id,
                    email = remote.email,
                    baseCurrency = remote.baseCurrency,
                    locale = remote.locale,
                    dailyReminderTime = remote.dailyReminderTime,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                )
            }
        }
        // Sin propagación de borrados acá a propósito — ver KDoc de la clase.
    }

    private suspend fun pullBudgets(currentUserId: String) {
        val remoteRows = supabaseClient.from("budget").select().decodeList<BudgetDto>()
        val remoteIds = remoteRows.map { it.id }.toSet()
        val localRows = db.budgetQueries.selectAllForSync().executeAsList()
        val localById = localRows.associateBy { it.id }
        for (remote in remoteRows) {
            val local = localById[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                db.budgetQueries.replaceFromRemote(
                    id = remote.id,
                    userId = remote.userId,
                    name = remote.name,
                    startDate = remote.startDate,
                    endDate = remote.endDate,
                    status = BudgetStatus.valueOf(remote.status),
                    initialBalance = remote.initialBalance,
                    baseCurrency = remote.baseCurrency,
                    notificationTime = remote.notificationTime,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                )
            }
        }
        propagateRemoteDeletes(
            localRows = localRows,
            remoteIds = remoteIds,
            currentUserId = currentUserId,
            id = { it.id },
            userId = { it.userId },
            syncStatus = { it.syncStatus },
            deleteLocalById = { db.budgetQueries.deleteById(it) },
        )
    }

    private suspend fun pullPlannedItems(currentUserId: String) {
        val remoteRows = supabaseClient.from("planned_item").select().decodeList<PlannedItemDto>()
        val remoteIds = remoteRows.map { it.id }.toSet()
        val localRows = db.plannedItemQueries.selectAllForSync().executeAsList()
        val localById = localRows.associateBy { it.id }
        for (remote in remoteRows) {
            val local = localById[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                db.plannedItemQueries.replaceFromRemote(
                    id = remote.id,
                    budgetId = remote.budgetId,
                    userId = remote.userId,
                    name = remote.name,
                    categoryId = remote.categoryId,
                    type = PlannedItemType.valueOf(remote.type),
                    amount = remote.amount,
                    currency = remote.currency,
                    frequency = PlannedItemFrequency.valueOf(remote.frequency),
                    billingDay = remote.billingDay?.toLong(),
                    specificDate = remote.specificDate,
                    dayOfWeek = remote.dayOfWeek?.let { DayOfWeek.valueOf(it) },
                    isActive = remote.isActive,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                )
            }
        }
        propagateRemoteDeletes(
            localRows = localRows,
            remoteIds = remoteIds,
            currentUserId = currentUserId,
            id = { it.id },
            userId = { it.userId },
            syncStatus = { it.syncStatus },
            deleteLocalById = { db.plannedItemQueries.deleteById(it) },
        )
    }

    private suspend fun pullProjectionSnapshots(currentUserId: String) {
        val remoteRows = supabaseClient.from("projection_snapshot").select().decodeList<ProjectionSnapshotDto>()
        val remoteIds = remoteRows.map { it.id }.toSet()
        val localRows = db.projectionSnapshotQueries.selectAllForSync().executeAsList()
        val localById = localRows.associateBy { it.id }
        for (remote in remoteRows) {
            val local = localById[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                db.projectionSnapshotQueries.replaceFromRemote(
                    id = remote.id,
                    budgetId = remote.budgetId,
                    userId = remote.userId,
                    label = remote.label,
                    frozenInitialBalance = remote.frozenInitialBalance,
                    frozenStartDate = remote.frozenStartDate,
                    frozenBaseCurrency = remote.frozenBaseCurrency,
                    frozenPlannedItems = remote.frozenPlannedItems.map { it.toDomain() },
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                )
            }
        }
        propagateRemoteDeletes(
            localRows = localRows,
            remoteIds = remoteIds,
            currentUserId = currentUserId,
            id = { it.id },
            userId = { it.userId },
            syncStatus = { it.syncStatus },
            deleteLocalById = { db.projectionSnapshotQueries.deleteById(it) },
        )
    }

    /**
     * Propagación de borrados entre dispositivos (agregado en el chat —
     * ver KDoc de la clase para el detalle completo). Genérica sobre el
     * tipo de fila de cada tabla: recibe cómo leer su `id`/`userId`/
     * `syncStatus` y cómo borrarla localmente, en vez de repetir el mismo
     * loop 5 veces.
     *
     * Filtrado por `userId == currentUserId` a propósito: no toca filas
     * de otra cuenta que puedan haber quedado localmente de un
     * `signOut()` anterior — esta app no borra la base local al cerrar
     * sesión (gap preexistente, separado de este cambio, ver KDoc de
     * `AuthRepositoryImpl.signOut`). Esto también excluye de forma
     * natural las categorías predefinidas (`userId == null`, ver
     * Category.sq): nunca son candidatas a este borrado.
     *
     * Filtrado por `syncStatus == SYNCED` (agregado en el chat, tras un
     * caso real detectado): una fila `PENDING` nunca es candidata a este
     * borrado, aunque no aparezca todavía en el pull remoto — recién se
     * creó, o un push anterior falló a mitad de camino y quedó esperando
     * el próximo ciclo (`push*()` corre antes que el `pull*()`
     * correspondiente en el mismo `syncNow()`, así que si el push de esta
     * misma fila tuvo éxito en este ciclo, ya va a aparecer en el pull).
     * Sin este filtro, una fila que nunca llegó a subirse (por ejemplo,
     * por el mismo tipo de bug ya documentado alguna vez con
     * `expense.sync_status`) se borraría localmente sin haber existido
     * jamás en el servidor — pérdida de datos irrecuperable, porque no
     * hay de dónde traerla de vuelta.
     */
    private fun <T> propagateRemoteDeletes(
        localRows: List<T>,
        remoteIds: Set<String>,
        currentUserId: String,
        id: (T) -> String,
        userId: (T) -> String?,
        syncStatus: (T) -> SyncStatus,
        deleteLocalById: (String) -> Unit,
    ) {
        for (row in localRows) {
            if (userId(row) == currentUserId && syncStatus(row) == SyncStatus.SYNCED && id(row) !in remoteIds) {
                deleteLocalById(id(row))
            }
        }
    }
}

/** Ver KDoc de `SyncManager.events`. */
sealed interface SyncEvent {
    /** Ver KDoc de `BudgetRepository.normalizeActiveStatus` — gap multi-dispositivo corregido en este ciclo de sync. */
    data class ActiveBudgetConflictResolved(
        val keptActiveName: String,
        val pausedNames: List<String>,
    ) : SyncEvent
}

/** Ver KDoc de `SyncManager` ("Estado del ciclo de sync"). */
sealed interface SyncCycleState {
    /** Todavía no corrió ningún `syncNow()` en esta sesión de la app. */
    data object Idle : SyncCycleState
    data object Syncing : SyncCycleState
    data class Success(val at: Instant) : SyncCycleState
    data class Failure(val at: Instant, val message: String) : SyncCycleState
}

private fun CategoryEntity.toDto() = CategoryDto(
    id = id,
    userId = userId,
    name = name,
    icon = icon,
    color = color,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ExpenseEntity.toDto() = ExpenseDto(
    id = id,
    userId = userId,
    amount = amount,
    currency = currency,
    categoryId = categoryId,
    date = date,
    note = note,
    receiptImageUrl = receiptImageUrl,
    paymentMethod = paymentMethod,
    createdAt = createdAt,
    updatedAt = updatedAt,
    budgetId = budgetId,
    plannedItemId = plannedItemId,
    isPlannedOverride = isPlannedOverride,
    type = type.name,
)

private fun UserSettingsEntity.toDto() = UserSettingsDto(
    id = id,
    email = email,
    baseCurrency = baseCurrency,
    locale = locale,
    dailyReminderTime = dailyReminderTime,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BudgetEntity.toDto() = BudgetDto(
    id = id,
    userId = userId,
    name = name,
    startDate = startDate,
    endDate = endDate,
    status = status.name,
    initialBalance = initialBalance,
    baseCurrency = baseCurrency,
    notificationTime = notificationTime,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * `billingDay` es `Long?` en la fila generada (SQLDelight mapea
 * `INTEGER` a `Long` por defecto) pero el DTO lo espera `Int?` (espeja
 * la columna `bigint`... en realidad `integer` en Postgres) — de ahí el
 * `?.toInt()`. Mismo motivo por el que `PlannedItemRepositoryImpl`/
 * `Mappers.kt` tienen la conversión análoga.
 */
private fun PlannedItemEntity.toDto() = PlannedItemDto(
    id = id,
    budgetId = budgetId,
    userId = userId,
    name = name,
    categoryId = categoryId,
    type = type.name,
    amount = amount,
    currency = currency,
    frequency = frequency.name,
    billingDay = billingDay?.toInt(),
    specificDate = specificDate,
    dayOfWeek = dayOfWeek?.name,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * `frozenPlannedItems` ya es `List<ProjectionSnapshotItem>` en la fila
 * generada (el ColumnAdapter de Adapters.kt decodifica el JSON al leer) —
 * acá solo se convierte cada entrada a su DTO (enum → String, mismo
 * criterio que PlannedItemEntity.toDto()).
 */
private fun ProjectionSnapshotEntity.toDto() = ProjectionSnapshotDto(
    id = id,
    budgetId = budgetId,
    userId = userId,
    label = label,
    frozenInitialBalance = frozenInitialBalance,
    frozenStartDate = frozenStartDate,
    frozenBaseCurrency = frozenBaseCurrency,
    frozenPlannedItems = frozenPlannedItems.map { it.toDto() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ProjectionSnapshotItem.toDto() = ProjectionSnapshotItemDto(
    plannedItemId = plannedItemId,
    name = name,
    type = type.name,
    amount = amount,
    currency = currency,
    frequency = frequency.name,
    billingDay = billingDay,
    specificDate = specificDate,
    dayOfWeek = dayOfWeek?.name,
    isActive = isActive,
)

private fun ProjectionSnapshotItemDto.toDomain() = ProjectionSnapshotItem(
    plannedItemId = plannedItemId,
    name = name,
    type = PlannedItemType.valueOf(type),
    amount = amount,
    currency = currency,
    frequency = PlannedItemFrequency.valueOf(frequency),
    billingDay = billingDay,
    specificDate = specificDate,
    dayOfWeek = dayOfWeek?.let { DayOfWeek.valueOf(it) },
    isActive = isActive,
)
