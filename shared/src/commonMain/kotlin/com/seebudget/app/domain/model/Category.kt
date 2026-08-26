package com.seebudget.app.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * RF-02 — Categorías predefinidas (Comida, Transporte, Vivienda, Ocio,
 * Salud, Otros) y categorías personalizadas creadas por el usuario, ambas
 * representadas con esta misma clase (`isDefault` las distingue). El seed
 * de las predefinidas se hace en la capa de persistencia.
 *
 * `userId`: null en las predefinidas (globales, compartidas por todo
 * usuario); con valor en las personalizadas (dueño). Ver políticas RLS en
 * supabase/migrations/0001_init_category_expense_rls.sql.
 *
 * `createdAt`/`updatedAt`/`syncStatus`/`deletedAt` (Fase 2, SyncManager):
 * mismo mecanismo que Expense — ver comentario ahí. `deletedAt` es un
 * tombstone local; una categoría "borrada" sigue existiendo en la tabla
 * hasta que el SyncManager confirma el borrado remoto.
 */
@Serializable
data class Category(
    val id: String, // UUID — ver nota de IDs en Expense.kt
    val userId: String?,
    val name: String,
    val icon: String,
    val color: String, // hex, ej. "#1B5E3F"
    val isDefault: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
    val deletedAt: Instant?,
)
