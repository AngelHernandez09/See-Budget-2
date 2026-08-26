package com.seebudget.app.data.repository

import com.seebudget.app.db.Budget as BudgetEntity
import com.seebudget.app.db.Category as CategoryEntity
import com.seebudget.app.db.Expense as ExpenseEntity
import com.seebudget.app.db.PlannedItem as PlannedItemEntity
import com.seebudget.app.db.ProjectionSnapshot as ProjectionSnapshotEntity
import com.seebudget.app.db.UserSettings as UserSettingsEntity
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.ProjectionSnapshot
import com.seebudget.app.domain.model.User

/**
 * `CategoryEntity`/`ExpenseEntity`/`UserSettingsEntity`/`BudgetEntity`/
 * `PlannedItemEntity`/`ProjectionSnapshotEntity` son las filas generadas
 * por SQLDelight (com.seebudget.app.db) — mismo nombre simple que sus
 * clases de dominio (com.seebudget.app.domain.model) salvo
 * `UserSettingsEntity`, que mapea a `User` (ver nota en User.kt sobre por
 * qué la tabla se llama distinto a la clase de dominio), por eso el alias
 * al importar.
 */
internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    userId = userId,
    name = name,
    icon = icon,
    color = color,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
)

internal fun ExpenseEntity.toDomain(): Expense = Expense(
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
    syncStatus = syncStatus,
    budgetId = budgetId,
    plannedItemId = plannedItemId,
    isPlannedOverride = isPlannedOverride,
    deletedAt = deletedAt,
    type = type,
)

internal fun UserSettingsEntity.toDomain(): User = User(
    id = id,
    email = email,
    baseCurrency = baseCurrency,
    locale = locale,
    dailyReminderTime = dailyReminderTime,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

/** Fase 5 — ver Budget.kt/Budget.sq. */
internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    userId = userId,
    name = name,
    startDate = startDate,
    endDate = endDate,
    status = status,
    initialBalance = initialBalance,
    baseCurrency = baseCurrency,
    notificationTime = notificationTime,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
)

/**
 * Fase 5/6 — ver PlannedItem.kt/PlannedItem.sq. `billingDay` es
 * `Long?` en la fila generada (SQLDelight mapea `INTEGER` a `Long` por
 * defecto, sin `AS`) pero `Int?` en el dominio (son días 1-31, no hace
 * falta `Long`) — de ahí el `?.toInt()`.
 */
internal fun PlannedItemEntity.toDomain(): PlannedItem = PlannedItem(
    id = id,
    budgetId = budgetId,
    userId = userId,
    name = name,
    categoryId = categoryId,
    type = type,
    amount = amount,
    currency = currency,
    frequency = frequency,
    billingDay = billingDay?.toInt(),
    specificDate = specificDate,
    dayOfWeek = dayOfWeek,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
)

/**
 * Fase 6 — ver ProjectionSnapshot.kt/ProjectionSnapshot.sq.
 * `frozenPlannedItems` ya llega como `List<ProjectionSnapshotItem>` acá
 * (el `ColumnAdapter` de Adapters.kt hace el JSON↔lista al leer la fila),
 * no hace falta ninguna conversión extra.
 */
internal fun ProjectionSnapshotEntity.toDomain(): ProjectionSnapshot = ProjectionSnapshot(
    id = id,
    budgetId = budgetId,
    userId = userId,
    label = label,
    frozenInitialBalance = frozenInitialBalance,
    frozenStartDate = frozenStartDate,
    frozenBaseCurrency = frozenBaseCurrency,
    frozenPlannedItems = frozenPlannedItems,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
)
