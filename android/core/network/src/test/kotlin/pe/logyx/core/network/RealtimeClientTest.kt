package pe.logyx.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.RealtimeStatus
import pe.logyx.core.model.RealtimeTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealtimeClientTest {
    private fun frame(id: Int, json: String) = "id: $id\ndata: $json\n\n"
    private val ready = """{"type":"stream.ready","seq":1,"data":{"replayed":0}}"""
    private fun bid(n: Int) = """{"type":"bid.placed","seq":$n,"data":{"requestId":"r1","bidCount":$n}}"""
    private val sse = headersOf(HttpHeaders.ContentType, "text/event-stream")

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(HttpTimeout)
        defaultRequest { url("http://localhost/") }
    }

    @Test fun `emite los eventos de dominio y deja el estado en vivo`() = runTest {
        val engine = MockEngine { respond(ByteReadChannel(": ping\n" + frame(1, ready) + frame(2, bid(2))), HttpStatusCode.OK, sse) }
        val rc = RealtimeClient(client(engine), tokenProvider = { "tok" })
        val e = rc.events().first()
        assertEquals(RealtimeTypes.BID_PLACED, e.type)
        assertEquals("r1", e.string("requestId"))
        assertEquals(2L, e.long("bidCount"))
        assertEquals("text/event-stream", engine.requestHistory.first().headers[HttpHeaders.Accept])
    }

    @Test fun `un 401 llama a onUnauthorized y no reintenta con el mismo token`() = runBlocking {
        var unauthorized = 0
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val rc = RealtimeClient(client(engine), tokenProvider = { "viejo" }, onUnauthorized = { unauthorized++ })
        val job = launch(Dispatchers.Default) { rc.events().collect { } }
        withTimeout(5_000) { while (unauthorized == 0) delay(10) }
        delay(300) // si reintentara, habría una segunda petición
        job.cancel()
        assertEquals(1, unauthorized)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(RealtimeStatus.OFFLINE, rc.status.value)
    }

    @Test fun `al reconectar envia Last-Event-ID y emite stream resync`() = runTest {
        var n = 0
        val engine = MockEngine {
            n++
            if (n == 1) respond(ByteReadChannel(frame(1, ready) + frame(5, bid(5))), HttpStatusCode.OK, sse)
            else respond(ByteReadChannel(frame(6, ready) + frame(7, bid(7))), HttpStatusCode.OK, sse)
        }
        val rc = RealtimeClient(client(engine), tokenProvider = { "tok" })
        val got = rc.events().take(3).toList()
        assertEquals(listOf(RealtimeTypes.BID_PLACED, RealtimeTypes.STREAM_RESYNC, RealtimeTypes.BID_PLACED), got.map { it.type })
        assertTrue(engine.requestHistory.size >= 2)
        assertEquals(null, engine.requestHistory[0].headers["Last-Event-ID"])
        assertEquals("5", engine.requestHistory[1].headers["Last-Event-ID"])
    }
}
