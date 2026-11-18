package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.DriverShipmentScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renovación de sesión ante un 401 (F24): una sola vez, con el token rechazado, y reintentando la petición original. */
class SessionRefreshTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val ts = """"meta":{"timestamp":"2026-11-17T20:00:00Z"}"""
    private val ok = """{"data":[],$ts}"""
    private val authResponse = """{"data":{"accessToken":"nuevo","expiresIn":3600,"refreshToken":"r2","refreshExpiresIn":2592000,
        "user":{"id":"u","email":"a@b.pe","fullName":"Ana","role":"DRIVER","organizationId":"o","organizationType":"CARRIER"}},$ts}"""

    private class FakeRefresher(var outcome: RefreshOutcome, val onRefresh: () -> Unit = {}) : SessionRefresher {
        val rejected = mutableListOf<String?>()
        override suspend fun refreshAfterUnauthorized(rejectedToken: String?): RefreshOutcome {
            rejected += rejectedToken
            onRefresh()
            return outcome
        }
    }

    /** Responde 401 si el token es "viejo" y 200 con el resto. */
    private fun engine(history: MutableList<HttpRequestData> = mutableListOf()) = MockEngine { req ->
        history += req
        if (req.headers[HttpHeaders.Authorization] == "Bearer viejo") respondError(HttpStatusCode.Unauthorized)
        else respond(ok, HttpStatusCode.OK, json)
    }

    @Test fun `un 401 renueva una vez y repite la peticion con el token nuevo`() = runTest {
        var token = "viejo"
        val engine = engine()
        val refresher = FakeRefresher(RefreshOutcome.RETRY, onRefresh = { token = "nuevo" })
        var unauthorized = 0
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { token }, engine), { unauthorized++ }, { refresher })

        val shipments = api.driverShipments()

        assertTrue(shipments.isEmpty())
        assertEquals(listOf<String?>("viejo"), refresher.rejected, "recibe el token rechazado")
        assertEquals(2, engine.requestHistory.size)
        assertEquals("Bearer viejo", engine.requestHistory[0].headers[HttpHeaders.Authorization])
        assertEquals("Bearer nuevo", engine.requestHistory[1].headers[HttpHeaders.Authorization])
        assertEquals(0, unauthorized)
    }

    @Test fun `si la sesion terminó propaga el 401 y cierra sesion una vez`() = runTest {
        val refresher = FakeRefresher(RefreshOutcome.SESSION_ENDED)
        var unauthorized = 0
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "viejo" }, engine()), { unauthorized++ }, { refresher })
        val e = assertFailsWith<ApiException> { api.driverShipments() }
        assertEquals(401, e.status)
        assertEquals(1, unauthorized)
        assertEquals(1, refresher.rejected.size)
    }

    @Test fun `si no se pudo renovar por red conserva la sesion y reporta error de red`() = runTest {
        val refresher = FakeRefresher(RefreshOutcome.UNAVAILABLE)
        var unauthorized = 0
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "viejo" }, engine()), { unauthorized++ }, { refresher })
        val e = assertFailsWith<ApiException> { api.driverShipments() }
        assertTrue(e.isNetwork)
        assertEquals(0, unauthorized)
    }

    @Test fun `si tras renovar sigue el 401 cierra sesion y no vuelve a renovar`() = runTest {
        val refresher = FakeRefresher(RefreshOutcome.RETRY) // el token no cambia: el servidor sigue rechazando
        var unauthorized = 0
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "viejo" }, engine()), { unauthorized++ }, { refresher })
        assertFailsWith<ApiException> { api.driverShipments() }
        assertEquals(1, refresher.rejected.size)
        assertEquals(1, unauthorized)
    }

    @Test fun `sin refresher un 401 cierra sesion como antes`() = runTest {
        var unauthorized = 0
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "viejo" }, engine()), { unauthorized++ })
        assertFailsWith<ApiException> { api.driverShipments() }
        assertEquals(1, unauthorized)
    }

    @Test fun `login con credenciales invalidas no renueva ni cierra sesion`() = runTest {
        val refresher = FakeRefresher(RefreshOutcome.RETRY)
        var unauthorized = 0
        val engine = MockEngine {
            respond("""{"error":{"code":"LOGYX-AUTH-001","message":"Correo o contraseña incorrectos"},$ts}""", HttpStatusCode.Unauthorized, json)
        }
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine), { unauthorized++ }, { refresher })
        val e = assertFailsWith<ApiException> { api.login("a@b.pe", "mala", refresh = true) }
        assertEquals("LOGYX-AUTH-001", e.code)
        assertTrue(refresher.rejected.isEmpty())
        assertEquals(0, unauthorized)
    }

    @Test fun `login pide refresh y el cuerpo viaja con refresh true`() = runTest {
        val engine = MockEngine { respond(authResponse, HttpStatusCode.OK, json) }
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { null }, engine))
        val auth = api.login(" a@b.pe ", "x", refresh = true)
        assertEquals("r2", auth.refreshToken)
        assertEquals(2_592_000L, auth.refreshExpiresIn)
        val body = engine.requestHistory.single().body.toByteArray().decodeToString()
        assertTrue(""""refresh":true""" in body, body)
        assertTrue(""""email":"a@b.pe"""" in body, body)
    }

    @Test fun `refresh va sin Authorization y no reintenta ante 401`() = runTest {
        val refresher = FakeRefresher(RefreshOutcome.RETRY)
        var unauthorized = 0
        val engine = MockEngine {
            respond("""{"error":{"code":"LOGYX-AUTH-009","message":"Tu sesión venció"},$ts}""", HttpStatusCode.Unauthorized, json)
        }
        // El cliente real añade el Bearer por defecto; refresh no debe disparar otra renovación.
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "viejo" }, engine), { unauthorized++ }, { refresher })
        val e = assertFailsWith<ApiException> { api.refresh("r1") }
        assertEquals("LOGYX-AUTH-009", e.code)
        assertTrue(refresher.rejected.isEmpty())
        assertEquals(0, unauthorized)
        assertTrue(""""refreshToken":"r1"""" in engine.requestHistory.single().body.toByteArray().decodeToString())
    }

    @Test fun `logout acepta 204 sin cuerpo y envia el refresh token`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
        api.logout("r9")
        api.logoutAll()
        assertEquals("/api/v1/auth/logout", engine.requestHistory[0].url.encodedPath)
        assertTrue(""""refreshToken":"r9"""" in engine.requestHistory[0].body.toByteArray().decodeToString())
        assertEquals("/api/v1/auth/logout-all", engine.requestHistory[1].url.encodedPath)
    }

    @Test fun `change-password devuelve sesion nueva y el error de campo llega con field`() = runTest {
        val engine = MockEngine { req ->
            val body = req.body.toByteArray().decodeToString()
            if ("incorrecta" in body) respond("""{"error":{"code":"LOGYX-AUTH-006","field":"currentPassword","message":"La contraseña actual no es correcta"},$ts}""", HttpStatusCode.BadRequest, json)
            else respond(authResponse, HttpStatusCode.OK, json)
        }
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
        assertEquals("nuevo", api.changePassword("buena", "nueva-clave-1").accessToken)
        val e = assertFailsWith<ApiException> { api.changePassword("incorrecta", "nueva-clave-1") }
        assertEquals("LOGYX-AUTH-006", e.code)
        assertEquals("currentPassword", e.field)
        assertTrue(""""refresh":true""" in engine.requestHistory[0].body.toByteArray().decodeToString())
    }

    @Test fun `forgot-password devuelve el mensaje y el historial usa scope`() = runTest {
        val engine = MockEngine { req ->
            if (req.url.encodedPath.endsWith("forgot-password")) respond("""{"data":{"message":"Si el correo existe, te enviamos un enlace"},$ts}""", HttpStatusCode.Accepted, json)
            else respond(ok, HttpStatusCode.OK, json)
        }
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
        assertEquals("Si el correo existe, te enviamos un enlace", api.forgotPassword(" a@b.pe ").message)
        api.driverShipments()
        api.driverShipments(DriverShipmentScope.HISTORY)
        assertNull(engine.requestHistory[1].url.parameters["scope"])
        assertEquals("HISTORY", engine.requestHistory[2].url.parameters["scope"])
    }

    // --- Tiempo real ---
    private val sse = headersOf(HttpHeaders.ContentType, "text/event-stream")
    private fun frame(id: Int, json: String) = "id: $id\ndata: $json\n\n"
    private val ready = """{"type":"stream.ready","seq":1,"data":{}}"""
    private fun bid(n: Int) = """{"type":"bid.placed","seq":$n,"data":{"requestId":"r1"}}"""
    private val expired = """{"type":"stream.expired","seq":3,"data":{}}"""

    /** Cliente cuyo Bearer sigue al token actual (como `createLogyxHttpClient`). */
    private fun streamClient(engine: MockEngine, token: () -> String) = HttpClient(engine) {
        install(HttpTimeout)
        defaultRequest { url("http://localhost/"); headers.append(HttpHeaders.Authorization, "Bearer ${token()}") }
    }

    @Test fun `el stream renueva ante un 401 y reconecta de inmediato con el token nuevo`() = runTest {
        var token = "viejo"
        val engine = MockEngine { req ->
            if (req.headers[HttpHeaders.Authorization] == "Bearer viejo") respondError(HttpStatusCode.Unauthorized)
            else respond(ByteReadChannel(frame(1, ready) + frame(2, bid(2))), HttpStatusCode.OK, sse)
        }
        val refresher = FakeRefresher(RefreshOutcome.RETRY, onRefresh = { token = "nuevo" })
        var unauthorized = 0
        val rc = RealtimeClient(streamClient(engine) { token }, tokenProvider = { token }, onUnauthorized = { unauthorized++ }, refresher = { refresher })
        val e = rc.events().first()
        assertEquals("bid.placed", e.type)
        assertEquals(listOf<String?>("viejo"), refresher.rejected)
        assertEquals(0, unauthorized)
        assertEquals("Bearer nuevo", engine.requestHistory.last().headers[HttpHeaders.Authorization])
    }

    @Test fun `stream expired renueva la sesion y reconecta con el token nuevo`() = runTest {
        var token = "t1"
        val engine = MockEngine { req ->
            if (req.headers[HttpHeaders.Authorization] == "Bearer t1") respond(ByteReadChannel(frame(1, ready) + frame(3, expired)), HttpStatusCode.OK, sse)
            else respond(ByteReadChannel(frame(4, ready) + frame(5, bid(5))), HttpStatusCode.OK, sse)
        }
        val refresher = FakeRefresher(RefreshOutcome.RETRY, onRefresh = { token = "t2" })
        val rc = RealtimeClient(streamClient(engine) { token }, tokenProvider = { token }, refresher = { refresher })
        val types = rc.events().let { flow ->
            val out = mutableListOf<String>()
            flow.first { out += it.type; it.type == "bid.placed" }
            out
        }
        assertEquals(listOf("stream.resync", "bid.placed"), types)
        assertEquals(listOf<String?>("t1"), refresher.rejected)
        assertEquals("Bearer t2", engine.requestHistory.last().headers[HttpHeaders.Authorization])
    }

    @Test fun `si el token renovado tambien da 401 cierra sesion sin renovar en bucle`() = kotlinx.coroutines.runBlocking {
        var token = "t1"
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val refresher = FakeRefresher(RefreshOutcome.RETRY, onRefresh = { token = "t2" })
        var unauthorized = 0
        val rc = RealtimeClient(streamClient(engine) { token }, tokenProvider = { token }, onUnauthorized = { unauthorized++ }, refresher = { refresher })
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { rc.events().collect { } }
        kotlinx.coroutines.withTimeout(5_000) { while (unauthorized == 0) kotlinx.coroutines.delay(10) }
        kotlinx.coroutines.delay(200)
        job.cancel()
        assertEquals(1, refresher.rejected.size)
        assertEquals(1, unauthorized)
    }
}
