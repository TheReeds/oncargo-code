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

enum class LoginFieldError { InvalidEmail, EmptyPassword }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val emailError: LoginFieldError? = null,
    val passwordError: LoginFieldError? = null,
    /** Error de la API o de red (credenciales inválidas, sin conexión...). */
    val error: String? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    /** Rellena credenciales (cuentas demo de desarrollo). */
    fun fill(email: String, password: String) = _state.update { it.copy(email = email, password = password, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val emailError = if (!s.email.contains("@") || s.email.isBlank()) LoginFieldError.InvalidEmail else null
        val passwordError = if (s.password.isBlank()) LoginFieldError.EmptyPassword else null
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            auth.login(s.email, s.password).fold(
                // Al iniciar sesión, AuthRepository.state cambia y la navegación raíz reacciona.
                onSuccess = { _state.update { it.copy(loading = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }
}
