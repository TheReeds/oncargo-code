package pe.logyx.core.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import pe.logyx.core.model.OrgPermission
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.Role
import pe.logyx.core.model.UserInfo
import pe.logyx.core.network.ApiException
import pe.logyx.core.network.LogyxApi
import pe.logyx.core.network.createLogyxHttpClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStorage(var session: StoredSession? = null) : SessionStorage {
    var cleared = 0
    override suspend fun read() = session
    override suspend fun save(session: StoredSession) { this.session = session }
    override suspend fun saveUser(user: UserInfo) { session = session?.copy(user = user) }
    override suspend fun clear() { session = null; cleared++ }
}

/** Sesión del móvil (F24): login con refresh, renovación única ante 401 concurrentes, reuso → logout, cambio de contraseña. */
class AuthRepositoryTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val ts = """"meta":{"timestamp":"2026-11-17T20:00:00Z"}"""
    private val user = UserInfo("u", "d@logyx.pe", "Juan", Role.DRIVER, "o", "Andes", OrgType.CARRIER, permissions = listOf(OrgPermission.READ))

    private fun userJson(role: String = "DRIVER", permissions: String = """["READ"]""") =
        """{"id":"u","email":"d@logyx.pe","fullName":"Juan","role":"$role","organizationId":"o","organizationType":"CARRIER","permissions":$permissions}"""

    private fun auth(access: String, refresh: String?) =
        """{"data":{"accessToken":"$access","expiresIn":3600,${refresh?.let { """"refreshToken":"$it","refreshExpiresIn":2592000,""" } ?: ""}"user":${userJson()}},$ts}"""

    private class Fixture(val repo: DefaultAuthRepository, val storage: MemoryStorage, val tokens: TokenHolder, val engine: MockEngine)

    private fun fixture(
        initial: StoredSession? = StoredSession("a1", "r1", user),
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): Fixture {
        val storage = MemoryStorage(initial)
        val tokens = TokenHolder()
        val engine = MockEngine(handler)
        lateinit var repo: DefaultAuthRepository
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val api = LogyxApi(
            createLogyxHttpClient("http://localhost", { tokens.token }, engine),
            onUnauthorized = { if (tokens.token != null) runBlocking { repo.logout() } },
            refresher = { repo },
        )
        repo = DefaultAuthRepository({ api }, storage, tokens, scope)
        return Fixture(repo, storage, tokens, engine).also { f ->
            runBlocking { withTimeout(5_000) { while (f.repo.state.value is SessionState.Loading) delay(5) } }
        }
    }

    private fun Fixture.api() = LogyxApi(
        createLogyxHttpClient("http://localhost", { tokens.token }, engine),
        onUnauthorized = { if (tokens.token != null) runBlocking { repo.logout() } },
        refresher = { repo },
    )

    @Test fun `login pide refresh y guarda access y refresh`() = runBlocking<Unit> {
        val f = fixture(initial = null) { req ->
            when (req.url.encodedPath) {
                "/api/v1/auth/login" -> respond(auth("a1", "r1"), HttpStatusCode.OK, json)
                else -> respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
            }
        }
        assertIs<SessionState.LoggedOut>(f.repo.state.value)
        val result = f.repo.login("d@logyx.pe", "Logyx2026!")
        assertTrue(result.isSuccess)
        val body = f.engine.requestHistory.first { it.url.encodedPath.endsWith("/login") }.body.toByteArray().decodeToString()
        assertTrue(""""refresh":true""" in body, body)
        assertEquals("a1", f.tokens.token)
        assertEquals("r1", f.storage.session?.refreshToken)
        assertIs<SessionState.LoggedIn>(f.repo.state.value)
    }

    @Test fun `cinco peticiones concurrentes con el access vencido rotan el refresh una sola vez`() = runBlocking<Unit> {
        val refreshCalls = AtomicInteger()
        val f = fixture { req ->
            when {
                req.url.encodedPath == "/api/v1/auth/refresh" -> {
                    delay(150) // las demás peticiones llegan con 401 mientras esta renueva
                    if (refreshCalls.incrementAndGet() == 1 && """"r1"""" in req.body.toByteArray().decodeToString()) respond(auth("a2", "r2"), HttpStatusCode.OK, json)
                    else respond("""{"error":{"code":"LOGYX-AUTH-009","message":"Refresh reutilizado"},$ts}""", HttpStatusCode.Unauthorized, json)
                }
                req.url.encodedPath == "/api/v1/auth/me" -> respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
                req.headers[HttpHeaders.Authorization] == "Bearer a2" -> respond("""{"data":[],$ts}""", HttpStatusCode.OK, json)
                else -> respond("""{"error":{"code":"LOGYX-AUTH-401","message":"vencido"},$ts}""", HttpStatusCode.Unauthorized, json)
            }
        }
        // el arranque ya hizo `me` (que renovó); restablecemos para medir solo la prueba concurrente
        f.tokens.token = "a1"
        f.storage.session = StoredSession("a1", "r1", user)
        refreshCalls.set(0)
        val api = f.api()
        val results = (1..5).map { async(Dispatchers.Default) { api.driverShipments() } }.awaitAll()
        assertEquals(5, results.size)
        assertEquals(1, refreshCalls.get(), "el refresh token rota una sola vez")
        assertEquals("a2", f.tokens.token)
        assertEquals("r2", f.storage.session?.refreshToken)
        assertIs<SessionState.LoggedIn>(f.repo.state.value)
    }

    @Test fun `reuso o refresh invalido (AUTH-009) cierra la sesion`() = runBlocking<Unit> {
        val f = fixture { req ->
            when (req.url.encodedPath) {
                "/api/v1/auth/refresh" -> respond("""{"error":{"code":"LOGYX-AUTH-009","message":"Tu sesión venció"},$ts}""", HttpStatusCode.Unauthorized, json)
                "/api/v1/auth/logout" -> respond("", HttpStatusCode.NoContent)
                else -> respond("""{"error":{"code":"LOGYX-AUTH-401","message":"vencido"},$ts}""", HttpStatusCode.Unauthorized, json)
            }
        }
        val e = assertFailsWith<ApiException> { f.api().driverShipments() }
        assertEquals(401, e.status)
        assertIs<SessionState.LoggedOut>(f.repo.state.value)
        assertNull(f.tokens.token)
        assertNull(f.storage.session)
    }

    @Test fun `sin red al renovar conserva la sesion y el refresh token`() = runBlocking<Unit> {
        val f = fixture { req ->
            when (req.url.encodedPath) {
                "/api/v1/auth/refresh" -> throw java.io.IOException("sin red")
                "/api/v1/auth/me" -> respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
                else -> respond("""{"error":{"code":"LOGYX-AUTH-401","message":"vencido"},$ts}""", HttpStatusCode.Unauthorized, json)
            }
        }
        val e = assertFailsWith<ApiException> { f.api().driverShipments() }
        assertTrue(e.isNetwork)
        assertIs<SessionState.LoggedIn>(f.repo.state.value)
        assertEquals("r1", f.storage.session?.refreshToken)
    }

    @Test fun `sesion guardada sin refresh token cierra sesion ante el 401 como antes`() = runBlocking<Unit> {
        val f = fixture(initial = StoredSession("a1", null, user)) { req ->
            if (req.url.encodedPath == "/api/v1/auth/me") respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
            else respond("""{"error":{"code":"LOGYX-AUTH-401","message":"vencido"},$ts}""", HttpStatusCode.Unauthorized, json)
        }
        assertFailsWith<ApiException> { f.api().driverShipments() }
        assertIs<SessionState.LoggedOut>(f.repo.state.value)
        assertTrue(f.engine.requestHistory.none { it.url.encodedPath.endsWith("/refresh") })
    }

    @Test fun `cambiar contraseña reemplaza access y refresh con la sesion nueva`() = runBlocking<Unit> {
        val f = fixture { req ->
            when (req.url.encodedPath) {
                "/api/v1/auth/change-password" -> respond(auth("a9", "r9"), HttpStatusCode.OK, json)
                else -> respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
            }
        }
        assertTrue(f.repo.changePassword("vieja-123", "nueva-clave-456").isSuccess)
        assertEquals("a9", f.tokens.token)
        assertEquals("r9", f.storage.session?.refreshToken)
        val body = f.engine.requestHistory.first { it.url.encodedPath.endsWith("change-password") }.body.toByteArray().decodeToString()
        assertTrue(""""refresh":true""" in body, body)
    }

    @Test fun `cambiar contraseña con la actual incorrecta devuelve el error de campo y conserva la sesion`() = runBlocking<Unit> {
        val f = fixture { req ->
            if (req.url.encodedPath.endsWith("change-password"))
                respond("""{"error":{"code":"LOGYX-AUTH-006","field":"currentPassword","message":"La contraseña actual no es correcta"},$ts}""", HttpStatusCode.BadRequest, json)
            else respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
        }
        val r = f.repo.changePassword("mala", "nueva-clave-456")
        val e = r.exceptionOrNull() as ApiException
        assertEquals("currentPassword", e.field)
        assertEquals("a1", f.tokens.token)
        assertIs<SessionState.LoggedIn>(f.repo.state.value)
    }

    @Test fun `logout borra lo local y revoca el refresh token en el servidor`() = runBlocking<Unit> {
        val f = fixture { req ->
            if (req.url.encodedPath.endsWith("/logout")) respond("", HttpStatusCode.NoContent)
            else respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
        }
        f.repo.logout()
        assertIs<SessionState.LoggedOut>(f.repo.state.value)
        assertNull(f.tokens.token)
        assertNull(f.storage.session)
        withTimeout(5_000) { while (f.engine.requestHistory.none { it.url.encodedPath.endsWith("/logout") }) delay(10) }
        val body = f.engine.requestHistory.first { it.url.encodedPath.endsWith("/logout") }.body.toByteArray().decodeToString()
        assertTrue(""""refreshToken":"r1"""" in body, body)
    }

    @Test fun `cerrar sesion en todos los dispositivos llama a logout-all y deja la sesion cerrada`() = runBlocking<Unit> {
        val f = fixture { req ->
            if (req.url.encodedPath.endsWith("/logout-all")) respond("", HttpStatusCode.NoContent)
            else respond("""{"data":${userJson()},$ts}""", HttpStatusCode.OK, json)
        }
        assertTrue(f.repo.logoutAll().isSuccess)
        assertIs<SessionState.LoggedOut>(f.repo.state.value)
        assertNull(f.storage.session)
    }

    @Test fun `olvide mi contraseña devuelve siempre el mensaje del servidor`() = runBlocking<Unit> {
        val f = fixture(initial = null) { respond("""{"data":{"message":"Si el correo está registrado, te enviamos un enlace."},$ts}""", HttpStatusCode.Accepted, json) }
        assertEquals("Si el correo está registrado, te enviamos un enlace.", f.repo.forgotPassword("x@y.pe").getOrNull())
    }

    @Test fun `refreshUser actualiza permisos y verificacion en la sesion`() = runBlocking<Unit> {
        var role = "DRIVER"
        val f = fixture { respond("""{"data":${userJson(role, """["READ","OPERATE"]""")},$ts}""", HttpStatusCode.OK, json) }
        role = "OPS"
        assertTrue(f.repo.refreshUser().isSuccess)
        val u = (f.repo.state.value as SessionState.LoggedIn).user
        assertEquals(Role.OPS, u.role)
        assertEquals(listOf(OrgPermission.READ, OrgPermission.OPERATE), u.permissions)
        assertEquals(Role.OPS, f.storage.session?.user?.role)
    }
}
