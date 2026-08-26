package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.AppLanguage
import com.seebudget.app.domain.model.ThemeMode
import com.seebudget.app.domain.model.User
import com.seebudget.app.domain.reminder.ExactAlarmPermission
import com.seebudget.app.data.sync.SyncCycleState
import com.seebudget.app.data.sync.SyncTrigger
import com.seebudget.app.domain.repository.AppPreferencesRepository
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * RF-04/Fase 3 — Ajustes generales (sección 8.4 #11 del documento): por
 * ahora expone la moneda base, (Fase 8, RF-05) el estado del permiso de
 * alarma exacta, (agregado en el chat, RNF-07) la apariencia
 * (claro/oscuro/sistema), y (agregado en el chat) la sección Cuenta
 * (email + cambio de contraseña). El resto de las secciones de esa
 * pantalla (sincronización, acerca de) se agregan en sus fases
 * correspondientes — "cerrar sesión" vive en `SettingsScreen.kt`, a la
 * misma altura del título "Ajustes" (antes estaba en el TopAppBar global
 * de MainShell en App.kt, que se quitó — ver KDoc de App.kt).
 *
 * Sección Cuenta (agregada en el chat): el modelo `User` solo tiene
 * `email` como dato de perfil — no hay nombre/avatar definidos en el
 * documento, así que "perfil" acá es simplemente mostrar el email (ver
 * `user`, ya existía). Se suma cambio de contraseña (`onChangePassword`),
 * una función nueva no definida en el documento original — decisión
 * tomada en el chat, a pedido explícito del usuario ("qué me
 * recomendás"), como el mínimo razonable para una sección de cuenta más
 * allá de solo mostrar el email. No hay borrado de cuenta: no existe
 * ningún flujo de cascada de borrado definido para eso (ver KDoc de
 * `User`, dominio).
 *
 * `exactAlarmGranted` (Fase 8, RF-05): a diferencia de `POST_NOTIFICATIONS`
 * (pedido con un diálogo estándar desde `MainActivity`), Android no
 * avisa a la app cuando el usuario concede `SCHEDULE_EXACT_ALARM` desde
 * Ajustes del sistema — por eso es un `StateFlow` que se actualiza a
 * pedido (`onRefreshExactAlarmStatus`, botón "Verificar" en
 * `SettingsScreen`) en vez de ser reactivo solo. Ver KDoc de
 * `ExactAlarmPermission`.
 *
 * `themeMode` (agregado en el chat, RNF-07): decisión explícita de que
 * esto es una preferencia local del dispositivo, no de la cuenta — no
 * pasa por `userSettingsRepository`. Ver KDoc completo en
 * `AppPreferencesRepository`/`AppPreferences` (domain/model). La UI
 * (`App.kt`) observa el mismo repositorio directo para decidir
 * `darkTheme` en `SeeBudgetTheme` — no hace falta pasar por este
 * ViewModel para eso, esto es solo para que Ajustes pueda cambiarlo.
 *
 * Sección Sincronización (agregada en el chat): `syncTrigger.syncState`
 * (passthrough de `SyncManager`, ver KDoc completo ahí) expone
 * Idle/Syncing/Success/Failure del último `syncNow()`, y `onSyncNow()`
 * dispara uno manual ("Sincronizar ahora"). No hay ningún estado propio
 * acá — este ViewModel solo reexpone lo que ya vive en `SyncTrigger`.
 */
class SettingsViewModel(
    private val userSettingsRepository: UserSettingsRepository,
    private val authRepository: AuthRepository,
    private val exactAlarmPermission: ExactAlarmPermission,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {

    val user: StateFlow<User?> = observeCurrentUser(userSettingsRepository, authRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exactAlarmGranted = MutableStateFlow(exactAlarmPermission.isGranted())
    val exactAlarmGranted: StateFlow<Boolean> = _exactAlarmGranted.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = appPreferencesRepository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** Ver KDoc de la clase — mismo patrón que themeMode, agregado en el chat (RNF-07, selector de idioma). */
    val language: StateFlow<AppLanguage> = appPreferencesRepository.observeLanguage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)

    fun onBaseCurrencyChange(currencyCode: String) {
        val current = user.value ?: return
        if (current.baseCurrency == currencyCode) return
        viewModelScope.launch {
            userSettingsRepository.update(current.copy(baseCurrency = currencyCode))
        }
    }

    fun onRequestExactAlarmPermission() = exactAlarmPermission.requestGrant()

    /** Vuelve a consultar el estado — ver KDoc de la clase sobre por qué no es automático. */
    fun onRefreshExactAlarmStatus() {
        _exactAlarmGranted.value = exactAlarmPermission.isGranted()
    }

    fun onThemeModeChange(mode: ThemeMode) {
        if (themeMode.value == mode) return
        viewModelScope.launch {
            appPreferencesRepository.setThemeMode(mode)
        }
    }

    fun onLanguageChange(language: AppLanguage) {
        if (this.language.value == language) return
        viewModelScope.launch {
            appPreferencesRepository.setLanguage(language)
        }
    }

    private val _passwordChangeStatus = MutableStateFlow<PasswordChangeStatus>(PasswordChangeStatus.Idle)
    val passwordChangeStatus: StateFlow<PasswordChangeStatus> = _passwordChangeStatus.asStateFlow()

    /**
     * Validación de "coinciden" es del lado del cliente (UX, evita un
     * typo silencioso); el resto (longitud mínima, etc.) lo valida
     * Supabase Auth del lado del servidor — no se duplica esa regla acá,
     * se muestra tal cual el mensaje de error que devuelva.
     */
    fun onChangePassword(newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            _passwordChangeStatus.value = PasswordChangeStatus.Done(success = false, error = PasswordChangeError.PasswordsDoNotMatch)
            return
        }
        viewModelScope.launch {
            _passwordChangeStatus.value = PasswordChangeStatus.Saving
            try {
                authRepository.updatePassword(newPassword)
                _passwordChangeStatus.value = PasswordChangeStatus.Done(success = true)
            } catch (e: Exception) {
                // RNF-07 (i18n, agregado en el chat): mismo criterio que
                // AuthErrorMessage/LoginScreen — el mensaje crudo de la
                // excepción viaja tal cual (no se traduce); el fallback SÍ
                // es texto nuestro, así que viaja como estado (no como
                // String ya armado) para que SettingsScreen lo resuelva
                // con stringResource().
                _passwordChangeStatus.value = PasswordChangeStatus.Done(
                    success = false,
                    error = e.message?.let { message -> PasswordChangeError.Raw(message) }
                        ?: PasswordChangeError.UpdateFailed,
                )
            }
        }
    }

    /** Vuelve a Idle — se llama al cerrar el diálogo, para que la próxima apertura no arranque mostrando el resultado anterior. */
    fun onDismissPasswordChangeStatus() {
        _passwordChangeStatus.value = PasswordChangeStatus.Idle
    }

    val syncState: StateFlow<SyncCycleState> = syncTrigger.syncState

    fun onSyncNow() {
        viewModelScope.launch {
            syncTrigger.syncNow()
        }
    }
}

/** Ver KDoc de `SettingsViewModel.onChangePassword`. */
sealed interface PasswordChangeStatus {
    data object Idle : PasswordChangeStatus
    data object Saving : PasswordChangeStatus
    data class Done(val success: Boolean, val error: PasswordChangeError? = null) : PasswordChangeStatus
}

/**
 * RNF-07 (i18n, agregado en el chat): mismo criterio que AuthErrorMessage
 * (AuthViewModel/LoginScreen) — describe QUÉ pasó sin el texto final ya
 * armado, SettingsScreen arma el Text(stringResource(...)) a partir de
 * esto. [Raw] es el mensaje crudo de una excepción (no se traduce, se
 * muestra tal cual).
 */
sealed interface PasswordChangeError {
    data object PasswordsDoNotMatch : PasswordChangeError
    data object UpdateFailed : PasswordChangeError
    data class Raw(val message: String) : PasswordChangeError
}

/**
 * Esta pantalla solo se muestra con sesión iniciada (ver App.kt), así que
 * `currentUserId()` no debería ser null acá — el `flowOf(null)` es
 * defensivo, no un caso esperado en la práctica.
 */
private fun observeCurrentUser(
    userSettingsRepository: UserSettingsRepository,
    authRepository: AuthRepository,
): Flow<User?> {
    val userId = authRepository.currentUserId() ?: return flowOf(null)
    return userSettingsRepository.observe(userId)
}
