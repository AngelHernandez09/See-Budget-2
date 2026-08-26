package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.AuthSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RF-07 — registro/login. `sessionState` es lo que consume App.kt para
 * decidir si mostrar LoginScreen o el shell principal.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val sessionState: StateFlow<AuthSessionState> = authRepository.observeSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthSessionState.Loading)

    private val _uiState = MutableStateFlow(AuthFormUiState())
    val uiState: StateFlow<AuthFormUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(isSignUp = !it.isSignUp, errorMessage = null, infoMessage = null)
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank() || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                if (state.isSignUp) {
                    val requiresConfirmation = authRepository.signUp(state.email, state.password)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            infoMessage = if (requiresConfirmation) {
                                AuthInfoMessage.SignUpConfirmationRequired
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    authRepository.signIn(state.email, state.password)
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        // RNF-07 (i18n, agregado en el chat): el texto crudo de la
                        // excepción se muestra tal cual (no es nuestro, no se
                        // traduce); el fallback SÍ es texto nuestro, así que viaja
                        // como estado (no como String ya armado) para que
                        // LoginScreen sea quien lo resuelva con stringResource().
                        errorMessage = e.message?.let { message -> AuthErrorMessage.Raw(message) }
                            ?: AuthErrorMessage.AuthenticationFailed,
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}

data class AuthFormUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: AuthErrorMessage? = null,
    val infoMessage: AuthInfoMessage? = null,
)

/**
 * RNF-07 (i18n, agregado en el chat): describe QUÉ pasó sin el texto final ya
 * armado — LoginScreen (Composable) arma el `Text(stringResource(...))` a
 * partir de esto. [Raw] es el mensaje crudo de una excepción (no se traduce,
 * se muestra tal cual); ver KDoc de AuthViewModel.submit sobre el criterio.
 */
sealed interface AuthErrorMessage {
    data class Raw(val text: String) : AuthErrorMessage
    data object AuthenticationFailed : AuthErrorMessage
}

/** Ver KDoc de [AuthErrorMessage] — mismo criterio para el mensaje informativo (no de error) de esta pantalla. */
sealed interface AuthInfoMessage {
    data object SignUpConfirmationRequired : AuthInfoMessage
}
