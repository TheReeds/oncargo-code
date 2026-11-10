package pe.logyx.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.model.UserInfo

class AccountViewModel(private val auth: AuthRepository) : ViewModel() {
    val user: StateFlow<UserInfo?> = auth.state
        .map { (it as? SessionState.LoggedIn)?.user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), (auth.state.value as? SessionState.LoggedIn)?.user)

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}
