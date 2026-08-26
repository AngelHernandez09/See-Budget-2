package com.seebudget.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * RF-07 — registro/login vía Supabase Auth. Solo email+password en este
 * primer corte (el documento marca Google/Apple Sign-In como "opcional",
 * no como parte del MVP obligatorio).
 *
 * `currentUserId()` es la fuente de verdad que usan ExpenseRepositoryImpl/
 * CategoryRepositoryImpl para poblar `userId` al crear un registro (ver
 * comentario en Expense.kt sobre por qué ese campo existe).
 *
 * `currentUserEmail()` (Fase 3): usado por UserSettingsRepositoryImpl para
 * poblar `email` al crear el perfil de usuario por primera vez
 * (`ensureProfile()`) — evita que esa capa tenga que conocer directamente
 * al `SupabaseClient`.
 *
 * `updatePassword()` (agregado en el chat — Ajustes, sección Cuenta):
 * cambia la contraseña de la sesión ya iniciada. Supabase Auth no pide la
 * contraseña actual para esto (confía en que la sesión ya está
 * autenticada) — no hay ningún paso de reautenticación adicional acá.
 */
interface AuthRepository {
    fun observeSession(): Flow<AuthSessionState>
    fun currentUserId(): String?
    fun currentUserEmail(): String?

    /**
     * @return `true` si Supabase requiere confirmación por email antes de
     * poder iniciar sesión (comportamiento default de un proyecto nuevo);
     * `false` si quedó logueado automáticamente.
     */
    suspend fun signUp(email: String, password: String): Boolean

    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
    suspend fun updatePassword(newPassword: String)
}

sealed interface AuthSessionState {
    /** Estado inicial mientras Auth restaura una sesión existente (si hay). */
    data object Loading : AuthSessionState
    data object SignedOut : AuthSessionState
    data class SignedIn(val userId: String) : AuthSessionState
}
