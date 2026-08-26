package com.seebudget.app.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * RF-08 — Un presupuesto independiente (saldo inicial, ítems planificados,
 * moneda base, fechas propias). La regla "solo uno ACTIVE a la vez" y las
 * transiciones automáticas (auto-activar al crear si no hay otro activo,
 * auto-completar al llegar `endDate`) son de negocio y se aplican en
 * `BudgetRepositoryImpl` (Fase 5, capa de persistencia/lógica), no acá —
 * este modelo permite representar cualquier estado individualmente
 * válido.
 *
 * `userId`/`createdAt`/`updatedAt`/`syncStatus`/`deletedAt` (Fase 5):
 * mismo mecanismo de sync offline-first que Expense/Category/
 * UserSettings — ver supabase/migrations/0005_budget_planned_item.sql.
 * A diferencia de `Category`, acá `userId` NO es nullable: no existe el
 * equivalente a "presupuesto predefinido/global".
 */
@Serializable
data class Budget(
    val id: String, // UUID — ver nota de IDs en Expense.kt
    val userId: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?, // null = sin límite (la proyección muestra hasta cuándo alcanza el saldo)
    val status: BudgetStatus,
    val initialBalance: Long, // unidades menores de `baseCurrency` — ver MoneyFormat
    val baseCurrency: String, // código ISO 4217
    val notificationTime: LocalTime?, // null = sin notificación de check-in diario
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
    val deletedAt: Instant?,
)

enum class BudgetStatus { ACTIVE, PAUSED, COMPLETED }
