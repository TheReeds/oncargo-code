package pe.logyx.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.userMessage

data class ForgotPasswordUiState(
    val email: String = "",
    val sending: Boolean = false,
    val emailInvalid: Boolean = false,
    /** Enviado: el mensaje es **siempre el mismo**, exista o no el correo (no revela qué correos están registrados). */
    val sent: Boolean = false,
    /** Texto del servidor; si viene vacío la pantalla usa el suyo. */
    val serverMessage: String? = null,
    /** Error de red o límite de intentos (429). */
    val error: String? = null,
)

/** "¿Olvidaste tu contraseña?": `POST /auth/forgot-password`. El enlace del correo abre la web `/restablecer`. */
class ForgotPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    /** Rellena con el correo que la persona ya escribió en el login. */
    fun prefill(email: String) {
        if (_state.value.email.isBlank()) _state.update { it.copy(email = email) }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailInvalid = false, error = null, sent = false, serverMessage = null) }

    fun submit() {
        val s = _state.value
        if (s.sending) return
        val email = s.email.trim()
        if (!email.contains("@") || email.length < 5) {
            _state.update { it.copy(emailInvalid = true) }
            return
        }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            auth.forgotPassword(email).fold(
                onSuccess = { message -> _state.update { it.copy(sending = false, sent = true, serverMessage = message.takeIf { m -> m.isNotBlank() }) } },
                onFailure = { e -> _state.update { it.copy(sending = false, error = e.userMessage()) } },
            )
        }
    }
}
