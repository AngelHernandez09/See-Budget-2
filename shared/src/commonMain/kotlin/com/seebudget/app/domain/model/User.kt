package com.seebudget.app.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * Perfil y preferencias del usuario (sección 7 del documento).
 *
 * No confundir con `UserInfo`/`UserSession` de supabase-kt Auth (Fase 2):
 * esa es la sesión de autenticación; esta es nuestro perfil de dominio. Si
 * ambas terminan importadas en el mismo archivo más adelante, hay que
 * calificar el import para evitar el choque de nombres.
 *
 * Persistencia (Fase 3): antes este modelo existía solo en el dominio, sin
 * tabla local ni remota — RF-04 ("el usuario selecciona una moneda base")
 * necesita guardarlo, así que se sincroniza igual que Category/Expense
 * (`createdAt`/`updatedAt`/`syncStatus`, LWW por `updatedAt`). La tabla
 * SQLDelight/Supabase se llama `UserSettings`/`user_settings` en vez de
 * `User`/`user` — evita el choque conceptual con `auth.users` de Supabase
 * Auth y la palabra reservada `user` en SQL — pero la clase de dominio se
 * mantiene como `User` para coincidir con el documento. `id` es el mismo
 * UUID que `auth.users.id` (no se genera uno nuevo al crear el perfil, a
 * diferencia de Expense/Category).
 *
 * Sin `deletedAt`/tombstone: no hay todavía ninguna funcionalidad de
 * "borrar mi perfil" definida en los RF — se agrega si en algún momento
 * hace falta, siguiendo el mismo patrón que Category/Expense.
 *
 * `baseCurrency`: por ahora se le asigna un default razonable ("USD") al
 * crear la fila (ver capa de lógica, próximo paso) y el usuario lo cambia
 * cuando quiera desde Ajustes generales (pantalla 11, sección
 * Preferencias) — no hay un flujo de onboarding separado para elegirlo la
 * primera vez, ya que ni el documento ni la conversación definen uno.
 */
@Serializable
data class User(
    val id: String, // UUID — igual a auth.users.id (Supabase Auth)
    val email: String,
    val baseCurrency: String, // código ISO 4217, ej. "USD" — ver CurrencyCatalog
    val locale: String, // ej. "es", "en" — RNF-07
    val dailyReminderTime: LocalTime?, // null = recordatorio general desactivado
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
)
