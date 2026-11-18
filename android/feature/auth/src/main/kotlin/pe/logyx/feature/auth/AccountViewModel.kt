package pe.logyx.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.model.UserInfo

data class AccountUiState(
    /** "Cerrar sesión en todos los dispositivos" en curso. */
    val loggingOutAll: Boolean = false,
    val message: UiMessage? = null,
)

class AccountViewModel(private val auth: AuthRepository) : ViewModel() {
    val user: StateFlow<UserInfo?> = auth.state
        .map { (it as? SessionState.LoggedIn)?.user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), (auth.state.value as? SessionState.LoggedIn)?.user)

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui.asStateFlow()

    init {
        // El estado de verificación o el rol pueden haber cambiado desde el último inicio: se vuelve a leer en silencio.
        viewModelScope.launch { auth.refreshUser() }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    /** `logout-all`: invalida todos los dispositivos, incluido este. Al terminar la navegación raíz vuelve al login. */
    fun logoutAll() {
        if (_ui.value.loggingOutAll) return
        _ui.update { it.copy(loggingOutAll = true) }
        viewModelScope.launch {
            auth.logoutAll().onFailure { e ->
                _ui.update { it.copy(loggingOutAll = false, message = UiMessage.error(e.userMessage())) }
            }
            // En éxito la sesión queda cerrada y esta pantalla desaparece.
        }
    }

    fun showMessage(message: UiMessage) = _ui.update { it.copy(message = message) }
    fun onMessageShown() = _ui.update { it.copy(message = null) }
}
