package com.seebudget.app.data.local

import app.cash.sqldelight.ColumnAdapter
import com.seebudget.app.db.AppPreferences
import com.seebudget.app.db.Budget
import com.seebudget.app.db.Category
import com.seebudget.app.db.Expense
import com.seebudget.app.db.ExchangeRate
import com.seebudget.app.db.PlannedItem
import com.seebudget.app.db.ProjectionSnapshot
import com.seebudget.app.db.UserSettings
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.ProjectionSnapshotItem
import com.seebudget.app.domain.model.SyncStatus
import com.seebudget.app.domain.model.AppLanguage
import com.seebudget.app.domain.model.ThemeMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLDelight trae adapter integrado solo para `INTEGER AS Boolean` — para
 * cualquier otro tipo declarado con `AS` en un .sq (LocalDate, LocalTime,
 * Instant, enums) hay que darle un ColumnAdapter explícito. Todos se
 * guardan como TEXT: fechas/horas/timestamps en ISO-8601 (formato nativo
 * de LocalDate.toString()/LocalTime.toString()/Instant.toString()), enums
 * por su nombre.
 */
object LocalDateAdapter : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}

object LocalTimeAdapter : ColumnAdapter<LocalTime, String> {
    override fun decode(databaseValue: String): LocalTime = LocalTime.parse(databaseValue)
    override fun encode(value: LocalTime): String = value.toString()
}

object InstantAdapter : ColumnAdapter<Instant, String> {
    override fun decode(databaseValue: String): Instant = Instant.parse(databaseValue)
    override fun encode(value: Instant): String = value.toString()
}

object SyncStatusAdapter : ColumnAdapter<SyncStatus, String> {
    override fun decode(databaseValue: String): SyncStatus = SyncStatus.valueOf(databaseValue)
    override fun encode(value: SyncStatus): String = value.name
}

/** Fase 5 — ver Budget.kt. */
object BudgetStatusAdapter : ColumnAdapter<BudgetStatus, String> {
    override fun decode(databaseValue: String): BudgetStatus = BudgetStatus.valueOf(databaseValue)
    override fun encode(value: BudgetStatus): String = value.name
}

/** Fase 5 — ver PlannedItem.kt. */
object PlannedItemTypeAdapter : ColumnAdapter<PlannedItemType, String> {
    override fun decode(databaseValue: String): PlannedItemType = PlannedItemType.valueOf(databaseValue)
    override fun encode(value: PlannedItemType): String = value.name
}

/** Fase 5 — ver PlannedItem.kt. */
object PlannedItemFrequencyAdapter : ColumnAdapter<PlannedItemFrequency, String> {
    override fun decode(databaseValue: String): PlannedItemFrequency = PlannedItemFrequency.valueOf(databaseValue)
    override fun encode(value: PlannedItemFrequency): String = value.name
}

/** Fase 6 — ver PlannedItem.kt (`dayOfWeek`, solo aplica si frequency == WEEKLY). */
object DayOfWeekAdapter : ColumnAdapter<DayOfWeek, String> {
    override fun decode(databaseValue: String): DayOfWeek = DayOfWeek.valueOf(databaseValue)
    override fun encode(value: DayOfWeek): String = value.name
}

/** Agregado en el chat, RNF-07 (tema claro/oscuro) — ver AppPreferences.kt. */
object ThemeModeAdapter : ColumnAdapter<ThemeMode, String> {
    override fun decode(databaseValue: String): ThemeMode = ThemeMode.valueOf(databaseValue)
    override fun encode(value: ThemeMode): String = value.name
}

/** Agregado en el chat, RNF-07 (selector de idioma) — ver AppPreferences.kt. */
object AppLanguageAdapter : ColumnAdapter<AppLanguage, String> {
    override fun decode(databaseValue: String): AppLanguage = AppLanguage.valueOf(databaseValue)
    override fun encode(value: AppLanguage): String = value.name
}

/**
 * Fase 6 — ver ProjectionSnapshot.kt. `frozenPlannedItems` es una lista
 * completa (no un id ni un enum), así que en vez de mapear a un `TEXT`
 * "simple" se serializa/deserializa como JSON con kotlinx.serialization —
 * mismo mecanismo con el que ya viajan los DTOs de red, acá aplicado a
 * una columna local.
 */
object ProjectionSnapshotItemsAdapter : ColumnAdapter<List<ProjectionSnapshotItem>, String> {
    override fun decode(databaseValue: String): List<ProjectionSnapshotItem> =
        Json.decodeFromString(databaseValue)
    override fun encode(value: List<ProjectionSnapshotItem>): String =
        Json.encodeToString(value)
}

/**
 * Adapter compuesto para la tabla `expense`, usado al construir AppDatabase
 * (ver AppModule.kt). Nota: `Expense` acá es la clase de fila generada por
 * SQLDelight (com.seebudget.app.db.Expense) — no la clase de dominio
 * (com.seebudget.app.domain.model.Expense). Mismo nombre, paquetes
 * distintos; ojo al importar ambas en un mismo archivo.
 */
fun expenseAdapter(): Expense.Adapter = Expense.Adapter(
    dateAdapter = LocalDateAdapter,
    createdAtAdapter = InstantAdapter,
    updatedAtAdapter = InstantAdapter,
    syncStatusAdapter = SyncStatusAdapter,
    deletedAtAdapter = InstantAdapter,
    typeAdapter = PlannedItemTypeAdapter,
)

/**
 * Adapter compuesto para `category` (Fase 2: agregó createdAt/updatedAt/
 * syncStatus para que participe del mismo mecanismo de sync que expense —
 * antes no hacía falta, `category` solo tenía `INTEGER AS Boolean`, que
 * SQLDelight resuelve solo). Mismo comentario que arriba sobre el
 * `Category` de com.seebudget.app.db vs. el de dominio.
 */
fun categoryAdapter(): Category.Adapter = Category.Adapter(
    createdAtAdapter = InstantAdapter,
    updatedAtAdapter = InstantAdapter,
    syncStatusAdapter = SyncStatusAdapter,
    deletedAtAdapter = InstantAdapter,
)

/**
 * Adapter compuesto para `UserSettings` (Fase 3 — ver UserSettings.sq y
 * User.kt). Mismo comentario que arriba: `UserSettings` acá es la fila
 * generada por SQLDelight, no un tipo de dominio propio (el modelo de
 * dominio correspondiente se llama `User`).
 */
fun userSettingsAdapter(): UserSettings.Adapter = UserSettings.Adapter(
    dailyReminderTimeAdapter = LocalTimeAdapter,
    createdAtAdapter = InstantAdapter,
    updatedAtAdapter = InstantAdapter,
    syncStatusAdapter = SyncStatusAdapter,
)

/**
 * Adapter compuesto para `ExchangeRate` (Fase 3 — caché local de tasas de
 * cambio, ver ExchangeRate.sq). Sin syncStatusAdapter: esta tabla no
 * participa del SyncManager (no tiene esa columna).
 */
fun exchangeRateAdapter(): ExchangeRate.Adapter = ExchangeRate.Adapter(
    dateAdapter = LocalDateAdapter,
    fetchedAtAdapter = InstantAdapter,
)

/**
 * Adapter compuesto para `Budget` (Fase 5 — ver Budget.sq).
 */
fun budgetAdapter(): Budget.Adapter = Budget.Adapter(
    startDateAdapter = LocalDateAdapter,
    endDateAdapter = LocalDateAdapter,
    statusAdapter = BudgetStatusAdapter,
    notificationTimeAdapter = LocalTimeAdapter,
    createdAtAdapter = InstantAdapter,
    updatedAtAdapter = InstantAdapter,
    syncStatusAdapter = SyncStatusAdapter,
    deletedAtAdapter = InstantAdapter,
)

/**
 * Adapter compuesto para `PlannedItem` (Fase 5/6 — ver PlannedItem.sq).
 */
fun plannedItemAdapter(): PlannedItem.Adapter = PlannedItem.Adapter(
    typeAdapter = PlannedItemTypeAdapter,
    frequencyAdapter = PlannedItemFrequencyAdapter,
    specificDateAdapter = LocalDateAdapter,
    dayOfWeekAdapter = DayOfWeekAdapter,
    createdAtAdapter = InstantAdapter,
    updatedAtAdapter = InstantAdapter,
    syncStatusAdapter = SyncStatusAdapter,
    deletedAtAdapter = InstantAdapter,
)

/**
 * Adapter compuesto para `ProjectionSnapshot` (Fase 6 — ver
 * ProjectionSnapshot.sq).
 */
fun projectionSnapshotAdapter(): ProjectionSnapshot.Adapter = ProjectionSnapshot.Adapter(
    frozenStartDateAdapter = LocalDateAdapter,
    frozenPlannedItemsAdapter = ProjectionSnapshotItemsAdapter,
    createdAtAdapter = InstantAdapter,
    updatedAtAdapter = InstantAdapter,
    syncStatusAdapter = SyncStatusAdapter,
    deletedAtAdapter = InstantAdapter,
)

/**
 * Adapter compuesto para `AppPreferences` (agregado en el chat, RNF-07 —
 * ver AppPreferences.sq/AppPreferences.kt). Sin syncStatusAdapter: tabla
 * local-only, no participa del SyncManager.
 */
fun appPreferencesAdapter(): AppPreferences.Adapter = AppPreferences.Adapter(
    themeModeAdapter = ThemeModeAdapter,
    languageAdapter = AppLanguageAdapter,
)
