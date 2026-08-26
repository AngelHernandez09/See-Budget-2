package com.seebudget.app.data.repository

import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.AuthSessionState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación sobre supabase-kt (`SupabaseClientProvider.client`,
 * inyectado vía Koin — ver AppModule.kt).
 *
 * Nota sobre persistencia de sesión: se usa el comportamiento default de
 * `install(Auth)` (sin `sessionManager` custom) — la API de
 * `SessionManager` por plataforma cambió entre versiones recientes de
 * supabase-kt y no pude verificar cuál aplica exactamente a la 3.2.5 de
 * este proyecto desde acá. Si al cerrar y reabrir la app pide login de
 * nuevo en vez de mantener la sesión, es el próximo ajuste a hacer (no
 * bloquea el resto de Fase 2).
 */
class AuthRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val db: AppDatabase,
) : AuthRepository {

    override fun observeSession(): Flow<AuthSessionState> =
        supabaseClient.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> AuthSessionState.SignedIn(
                    userId = status.session.user?.id.orEmpty(),
                )
                is SessionStatus.NotAuthenticated -> AuthSessionState.SignedOut
                // Sesión expirada y no se pudo refrescar: se trata igual que
                // "sin sesión" (pide login de nuevo) en vez de spinner infinito.
                is SessionStatus.RefreshFailure -> AuthSessionState.SignedOut
                SessionStatus.Initializing -> AuthSessionState.Loading
            }
        }

    override fun currentUserId(): String? = supabaseClient.auth.currentUserOrNull()?.id

    override fun currentUserEmail(): String? = supabaseClient.auth.currentUserOrNull()?.email

    override suspend fun signUp(email: String, password: String): Boolean {
        val user = supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // signUpWith devuelve el usuario (no-nulo) sin loguearlo cuando el
        // proyecto requiere confirmación por email; devuelve null y loguea
        // automático cuando la confirmación está deshabilitada.
        return user != null
    }

    override suspend fun signIn(email: String, password: String) {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Bug reportado por el usuario (multi-cuenta en el mismo dispositivo,
     * agregado en el chat): esto antes solo cerraba la sesión en
     * Supabase, sin tocar el SQLite local — la próxima cuenta que
     * iniciaba sesión en el mismo dispositivo veía los datos de la
     * cuenta anterior, porque las queries de lectura (`selectAll`, etc.)
     * nunca filtraron por `userId` (pensadas para una sola cuenta a la
     * vez por dispositivo, no multi-tenant). Se vacían acá las tablas
     * específicas de cuenta (`deleteAll`, ver KDoc en cada `.sq`) — NO
     * se toca `AppPreferences` (preferencia del dispositivo, no de la
     * cuenta) ni `ExchangeRate` (caché público, no pertenece a ningún
     * usuario). El siguiente `signIn` dispara el sync inicial normal
     * (`SyncTrigger.ensureProfile` + `syncNow`), que repuebla todo desde
     * cero para la cuenta que corresponda.
     */
    override suspend fun signOut() {
        supabaseClient.auth.signOut()
        db.transaction {
            db.categoryQueries.deleteAll()
            db.expenseQueries.deleteAll()
            db.budgetQueries.deleteAll()
            db.plannedItemQueries.deleteAll()
            db.projectionSnapshotQueries.deleteAll()
            db.userSettingsQueries.deleteAll()
        }
    }

    override suspend fun updatePassword(newPassword: String) {
        supabaseClient.auth.updateUser {
            password = newPassword
        }
    }
}
