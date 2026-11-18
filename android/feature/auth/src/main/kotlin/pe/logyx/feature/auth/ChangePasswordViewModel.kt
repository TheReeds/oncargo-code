package pe.logyx.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.FieldErrors
import pe.logyx.core.data.fieldErrors
import pe.logyx.core.data.userMessage

/** Errores locales del formulario (el texto lo resuelve la pantalla) para no depender de `Context` aquí. */
enum class PasswordFormError { CurrentEmpty, NewTooShort, NewTooLong, NewSameAsCurrent, ConfirmMismatch }

data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val saving: Boolean = false,
    val currentError: PasswordFormError? = null,
    val newError: PasswordFormError? = null,
    val confirmError: PasswordFormError? = null,
    /** Errores del servidor bajo el campo: `AUTH-006` → `currentPassword`, `AUTH-010` → `newPassword`. */
    val serverErrors: FieldErrors = FieldErrors.NONE,
    /** Otro error (red, 429 tras 10 intentos fallidos). */
    val error: String? = null,
    /** Se cambió: la pantalla cierra el formulario y muestra el aviso; las demás sesiones quedaron cerradas. */
    val done: Boolean = false,
) {
    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 72
    }
}

/** Cambiar contraseña (`POST /auth/change-password`, V14): guarda la sesión nueva que devuelve el servidor. */
class ChangePasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun onCurrentChange(v: String) = _state.update { it.copy(current = v, currentError = null, serverErrors = it.serverErrors.without("currentPassword"), error = null) }
    fun onNewChange(v: String) = _state.update { it.copy(new = v, newError = null, serverErrors = it.serverErrors.without("newPassword"), error = null) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirm = v, confirmError = null, error = null) }

    /** Valida lo que se puede validar sin red (8–72 caracteres, distinta de la actual, confirmación igual). */
    private fun validate(s: ChangePasswordUiState): ChangePasswordUiState {
        val currentError = if (s.current.isEmpty()) PasswordFormError.CurrentEmpty else null
        val newError = when {
            s.new.length < ChangePasswordUiState.MIN_LENGTH -> PasswordFormError.NewTooShort
            s.new.length > ChangePasswordUiState.MAX_LENGTH -> PasswordFormError.NewTooLong
            s.new == s.current -> PasswordFormError.NewSameAsCurrent
            else -> null
        }
        val confirmError = if (newError == null && s.confirm != s.new) PasswordFormError.ConfirmMismatch else null
        return s.copy(currentError = currentError, newError = newError, confirmError = confirmError)
    }

    fun submit() {
        if (_state.value.saving) return
        val checked = validate(_state.value)
        if (checked.currentError != null || checked.newError != null || checked.confirmError != null) {
            _state.value = checked
            return
        }
        _state.update { checked.copy(saving = true, error = null, serverErrors = FieldErrors.NONE) }
        viewModelScope.launch {
            auth.changePassword(checked.current, checked.new).fold(
                onSuccess = { _state.update { ChangePasswordUiState(done = true) } },
                onFailure = { e ->
                    val fields = e.fieldErrors("currentPassword", "newPassword")
                    _state.update {
                        it.copy(saving = false, serverErrors = fields, error = if (fields.isEmpty) e.userMessage() else null)
                    }
                },
            )
        }
    }

    fun onDoneShown() = _state.update { it.copy(done = false) }
}
