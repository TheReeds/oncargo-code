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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import pe.logyx.core.model.AuthResponse
import pe.logyx.core.model.UserInfo
import pe.logyx.core.network.ApiException
import pe.logyx.core.network.LogyxApi
import pe.logyx.core.network.LogyxJson
import pe.logyx.core.network.RefreshOutcome
import pe.logyx.core.network.SessionRefresher

/** Estado de la sesión: la UI raíz decide pantalla según este valor. */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: UserInfo) : SessionState
}

/** Usuario de la sesión actual, o null si no hay sesión. */
val AuthRepository.sessionUser: UserInfo? get() = (state.value as? SessionState.LoggedIn)?.user

interface AuthRepository {
    val state: StateFlow<SessionState>

    /** Inicia sesión pidiendo refresh token (F24). Los demás datos de la sesión salen de la respuesta. */
    suspend fun login(email: String, password: String): Result<UserInfo>

    /** Cierra la sesión de este dispositivo: borra lo local y, a mejor esfuerzo, revoca el refresh token en el servidor. */
    suspend fun logout()

    /** Vuelve a pedir `GET /auth/me` (verificación, permisos y rol pueden haber cambiado). Silencioso: no cierra sesión por sí solo. */
    suspend fun refreshUser(): Result<UserInfo> = Result.failure(UnsupportedOperationException())

    /** `POST /auth/change-password`: el servidor cierra las demás sesiones y devuelve una nueva, que reemplaza a esta. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /** `POST /auth/logout-all`: invalida todos los dispositivos, incluido este (queda sin sesión). */
    suspend fun logoutAll(): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** `POST /auth/forgot-password`: devuelve el mensaje del servidor (idéntico exista o no el correo). */
    suspend fun forgotPassword(email: String): Result<String> = Result.failure(UnsupportedOperationException())
}

/** Sesión guardada en disco. */
data class StoredSession(val accessToken: String, val refreshToken: String?, val user: UserInfo)

interface SessionStorage {
    suspend fun read(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun saveUser(user: UserInfo)
    suspend fun clear()
}

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "logyx_session")

/**
 * Persiste la sesión en DataStore. El **refresh token** (credencial de 30 días) va cifrado con [cipher]
 * (AES-GCM con clave del Android Keystore); el access token (1 h) y el usuario van en claro como antes.
 * Si el refresh no se puede descifrar se trata como ausente: el usuario conserva la sesión hasta que venza el access y
 * entonces vuelve a iniciar sesión.
 */
class SessionStore(context: Context, private val cipher: SecretCipher = KeystoreSecretCipher()) : SessionStorage {
    private val store = context.applicationContext.sessionDataStore
    private val tokenKey = stringPreferencesKey("token")
    private val refreshKey = stringPreferencesKey("refresh_enc")
    private val userKey = stringPreferencesKey("user")

    override suspend fun read(): StoredSession? {
        val prefs = store.data.first()
        val token = prefs[tokenKey] ?: return null
        val user = prefs[userKey]?.let { runCatching { LogyxJson.decodeFromString<UserInfo>(it) }.getOrNull() }
            ?: return null
        val refresh = prefs[refreshKey]?.let(cipher::decrypt)
        return StoredSession(token, refresh, user)
    }

    override suspend fun save(session: StoredSession) {
        val encrypted = session.refreshToken?.let { runCatching { cipher.encrypt(it) }.getOrNull() }
        store.edit {
            it[tokenKey] = session.accessToken
            if (encrypted != null) it[refreshKey] = encrypted else it.remove(refreshKey)
            it[userKey] = LogyxJson.encodeToString(UserInfo.serializer(), session.user)
        }
    }

    override suspend fun saveUser(user: UserInfo) {
        store.edit { it[userKey] = LogyxJson.encodeToString(UserInfo.serializer(), user) }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }
}

/**
 * Sesión del móvil. Además de login/logout implementa [SessionRefresher] (F24): ante un 401 `LogyxApi` y el cliente SSE
 * piden [refreshAfterUnauthorized]; las renovaciones se **serializan con un Mutex** y quien llega tarde ve que el token ya
 * cambió y solo reintenta, así el refresh token (que rota en cada uso) se gasta una sola vez. Si el servidor responde 401
 * (`AUTH-009`: usado, vencido, revocado) o no hay refresh token, la sesión se cierra.
 */
class DefaultAuthRepository(
    private val api: () -> LogyxApi,
    private val store: SessionStorage,
    private val tokenHolder: TokenHolder,
    private val scope: CoroutineScope,
) : AuthRepository, SessionRefresher {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = _state

    private val refreshMutex = Mutex()

    /** Sube con cada login/logout; una renovación en vuelo que termina en otra "generación" se descarta. */
    @Volatile private var generation = 0

    init {
        scope.launch {
            val saved = store.read()
            if (saved == null) {
                _state.value = SessionState.LoggedOut
            } else {
                tokenHolder.token = saved.accessToken
                _state.value = SessionState.LoggedIn(saved.user)
                refreshUser() // la verificación o los permisos pudieron cambiar mientras la app estaba cerrada
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<UserInfo> = apiCall {
        val auth = api().login(email, password, refresh = true)
        applySession(auth)
        auth.user
    }

    override suspend fun refreshUser(): Result<UserInfo> = apiCall {
        val user = api().me()
        if (tokenHolder.token != null && _state.value is SessionState.LoggedIn) {
            store.saveUser(user)
            _state.value = SessionState.LoggedIn(user)
        }
        user
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = apiCall {
        val auth = api().changePassword(currentPassword, newPassword, refresh = true)
        applySession(auth)
    }

    override suspend fun logoutAll(): Result<Unit> = apiCall {
        api().logoutAll()
        clearLocal()
    }

    override suspend fun forgotPassword(email: String): Result<String> = apiCall { api().forgotPassword(email).message }

    override suspend fun logout() {
        val refresh = runCatching { store.read()?.refreshToken }.getOrNull()
        clearLocal()
        if (refresh != null) {
            // Mejor esfuerzo y sin bloquear la salida: si falla, el refresh token vence solo.
            scope.launch { withTimeoutOrNull(REMOTE_LOGOUT_TIMEOUT_MS) { runCatching { api().logout(refresh) } } }
        }
    }

    override suspend fun refreshAfterUnauthorized(rejectedToken: String?): RefreshOutcome = refreshMutex.withLock {
        val current = tokenHolder.token ?: return@withLock RefreshOutcome.SESSION_ENDED
        // Otra petición ya renovó mientras esta esperaba el candado: solo hay que reintentar con el token nuevo.
        if (rejectedToken != null && rejectedToken != current) return@withLock RefreshOutcome.RETRY
        val startedAt = generation
        val refresh = store.read()?.refreshToken
        if (refresh == null) { // sesión anterior a F24 o refresh ilegible: como antes, hay que volver a iniciar sesión
            clearLocal()
            return@withLock RefreshOutcome.SESSION_ENDED
        }
        val auth = try {
            api().refresh(refresh)
        } catch (e: ApiException) {
            // Red caída, 5xx o límite de intentos: la sesión sigue siendo válida, se reintentará. 401/400: no hay arreglo.
            if (e.isNetwork || e.status >= 500 || e.status == 429) return@withLock RefreshOutcome.UNAVAILABLE
            clearLocal()
            return@withLock RefreshOutcome.SESSION_ENDED
        }
        if (generation != startedAt) return@withLock RefreshOutcome.SESSION_ENDED // cerraron sesión mientras tanto
        applySession(auth)
        RefreshOutcome.RETRY
    }

    private suspend fun applySession(auth: AuthResponse) {
        generation++
        tokenHolder.token = auth.accessToken
        store.save(StoredSession(auth.accessToken, auth.refreshToken, auth.user))
        _state.value = SessionState.LoggedIn(auth.user)
    }

    private suspend fun clearLocal() {
        generation++
        tokenHolder.token = null
        store.clear()
        _state.value = SessionState.LoggedOut
    }

    private companion object {
        const val REMOTE_LOGOUT_TIMEOUT_MS = 5_000L
    }
}

/** Token JWT en memoria, leído por el cliente HTTP en cada petición. */
class TokenHolder {
    @Volatile
    var token: String? = null
}
