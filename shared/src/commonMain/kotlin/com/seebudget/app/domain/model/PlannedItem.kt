package com.seebudget.app.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * RF-09 — Ítem periódico o no periódico (ingreso/egreso) que el
 * `ProjectionEngine` (Fase 6) usa como plantilla para simular días
 * futuros.
 *
 * `name` (Fase 6, agregado a pedido explícito en el chat): el documento
 * no lo listaba en el modelo original de PlannedItem, pero sin un nombre
 * no hay forma de distinguir "Alquiler" de "Netflix" en la lista de
 * ítems planificados — puramente un gap del documento, no una regla de
 * negocio nueva.
 *
 * `specificDate`/`dayOfWeek` (Fase 6): el modelo original no alcanzaba
 * para simular con precisión — ONCE no tenía ninguna fecha propia, y
 * WEEKLY no definía qué día de la semana. Mismo patrón que `billingDay`
 * para MONTHLY: nullable a nivel de columna, pero obligatorio según la
 * `frequency` elegida (ver `ProjectionEngine`/validación en
 * BudgetFormScreen):
 * - `specificDate` solo tiene sentido (y es obligatorio) si `frequency == ONCE`.
 * - `dayOfWeek` solo tiene sentido (y es obligatorio) si `frequency == WEEKLY`.
 * - `billingDay` solo tiene sentido (y es obligatorio) si `frequency == MONTHLY`.
 * - `WEEKDAYS` no necesita ninguno de los tres (aplica Lun-Vie siempre).
 *
 * `categoryId` (Fase 7, agregado en el chat): resuelve un gap detectado
 * al diseñar el Check-in diario (RF-10) — un `Expense` siempre requiere
 * `categoryId` (RF-01), pero `PlannedItem` no tenía ninguna. Se define
 * una sola vez acá, al crear/editar el ítem planificado, para que
 * confirmar un ítem en el Check-in (un toque de checkbox) no tenga que
 * pedir categoría en el momento. Nullable a nivel de columna por
 * compatibilidad con los `PlannedItem` creados antes de Fase 7 (que
 * todavía no tienen categoría); el formulario de creación/edición
 * (Ajustes del presupuesto, pantalla 10) la va a pedir como obligatoria
 * para ítems nuevos — pendiente de construir esa parte de la UI. Un
 * `PlannedItem` sin `categoryId` no se puede confirmar desde el Check-in
 * hasta que se le asigne una (ver `CheckInViewModel`).
 *
 * `userId` (NOT NULL, denormalizado): igual que Expense/Category, en vez
 * de resolver RLS vía join con `budget.userId` — decisión tomada en el
 * chat para mantener las políticas RLS simples.
 *
 * `createdAt`/`updatedAt`/`syncStatus`/`deletedAt`: mismo mecanismo de
 * sync offline-first que el resto del modelo.
 */
@Serializable
data class PlannedItem(
    val id: String, // UUID — ver nota de IDs en Expense.kt
    val budgetId: String,
    val userId: String,
    val name: String,
    // Ver KDoc de la clase — obligatorio en la práctica desde Fase 7, nullable por compatibilidad con ítems previos.
    val categoryId: String?,
    val type: PlannedItemType,
    val amount: Long, // unidades menores de `currency` — ver MoneyFormat
    val currency: String, // código ISO 4217
    val frequency: PlannedItemFrequency,
    // Solo aplica si frequency == MONTHLY (1..31). El ajuste automático en
    // meses cortos (ej. billingDay=31 en febrero) lo resuelve el
    // ProjectionEngine.
    val billingDay: Int?,
    // Solo aplica si frequency == ONCE.
    val specificDate: LocalDate?,
    // Solo aplica si frequency == WEEKLY.
    val dayOfWeek: DayOfWeek?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
    val deletedAt: Instant?,
)

enum class PlannedItemType { INCOME, EXPENSE }

/**
 * El documento (sección 7) dice "once/weekly/WEEKDAYS/monthly..." — la
 * "..." sugiere que la lista podría no ser exhaustiva. Implementados los
 * 4 valores explícitos del documento más `DAILY` (agregado a pedido
 * explícito en el chat): todos los días, incluido fin de semana — a
 * diferencia de `WEEKDAYS` (Lun-Vie). No necesita ningún campo extra
 * (`billingDay`/`specificDate`/`dayOfWeek` quedan `null`), mismo caso que
 * `WEEKDAYS`. Ver `PlannedItemOccurrence.occursOn`.
 */
enum class PlannedItemFrequency { ONCE, DAILY, WEEKLY, WEEKDAYS, MONTHLY }
