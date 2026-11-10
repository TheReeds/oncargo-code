package pe.logyx.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.logyx.core.model.UserInfo
import pe.logyx.core.network.LogyxApi
import pe.logyx.core.network.LogyxJson

/** Estado de la sesión: la UI raíz decide pantalla según este valor. */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: UserInfo) : SessionState
}

interface AuthRepository {
    val state: StateFlow<SessionState>
    suspend fun login(email: String, password: String): Result<UserInfo>
    suspend fun logout()
}

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "logyx_session")

/** Persiste token y usuario en DataStore; expone el token en memoria para el cliente HTTP. */
class SessionStore(context: Context) {
    private val store = context.applicationContext.sessionDataStore
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user")

    suspend fun read(): Pair<String, UserInfo>? {
        val prefs = store.data.first()
        val token = prefs[tokenKey] ?: return null
        val user = prefs[userKey]?.let { runCatching { LogyxJson.decodeFromString<UserInfo>(it) }.getOrNull() }
            ?: return null
        return token to user
    }

    suspend fun save(token: String, user: UserInfo) {
        store.edit {
            it[tokenKey] = token
            it[userKey] = LogyxJson.encodeToString(UserInfo.serializer(), user)
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }
}

class DefaultAuthRepository(
    private val api: () -> LogyxApi,
    private val store: SessionStore,
    private val tokenHolder: TokenHolder,
    scope: CoroutineScope,
) : AuthRepository {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = _state

    init {
        scope.launch {
            val saved = store.read()
            if (saved == null) {
                _state.value = SessionState.LoggedOut
            } else {
                tokenHolder.token = saved.first
                _state.value = SessionState.LoggedIn(saved.second)
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<UserInfo> = apiCall {
        val auth = api().login(email, password)
        tokenHolder.token = auth.accessToken
        store.save(auth.accessToken, auth.user)
        _state.value = SessionState.LoggedIn(auth.user)
        auth.user
    }

    override suspend fun logout() {
        tokenHolder.token = null
        store.clear()
        _state.value = SessionState.LoggedOut
    }
}

/** Token JWT en memoria, leído por el cliente HTTP en cada petición. */
class TokenHolder {
    @Volatile
    var token: String? = null
}
