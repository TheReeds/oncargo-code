package pe.logyx.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import pe.logyx.core.model.RealtimeEvent
import pe.logyx.core.model.RealtimeStatus
import pe.logyx.core.model.RealtimeTypes
import kotlin.random.Random

/**
 * Cliente SSE de `GET /api/v1/events/stream` (ADR-0007), Kotlin puro sobre Ktor (compartible con iOS vía KMP).
 *
 * [events] es un Flow frío que, mientras se colecta, mantiene la conexión: reconecta con espera exponencial (1 s → 30 s)
 * enviando `Last-Event-ID`, emite los eventos de dominio y, al reconectar tras una caída (o si el servidor pide
 * `stream.resync`), emite un evento [RealtimeTypes.STREAM_RESYNC] para que la app refresque lo que muestra.
 * Un 401 llama a [onUnauthorized] (sesión vencida) y deja de reintentar hasta que haya un token nuevo.
 * El latido del servidor (`: ping` cada ~20 s) mantiene viva la conexión; si no llega nada en [socketTimeoutMs],
 * se da por caída y se reconecta.
 */
class RealtimeClient(
    private val client: HttpClient,
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit = {},
    private val socketTimeoutMs: Long = 60_000,
    private val maxBackoffMs: Long = 30_000,
) {
    private val _status = MutableStateFlow(RealtimeStatus.CONNECTING)
    val status: StateFlow<RealtimeStatus> = _status.asStateFlow()

    fun events(): Flow<RealtimeEvent> = channelFlow {
        var lastId: String? = null
        var backoff = 1_000L
        var everLive = false
        var rejectedToken: String? = null
        while (currentCoroutineContext().isActive) {
            val token = tokenProvider()
            if (token == null || token == rejectedToken) {
                _status.value = RealtimeStatus.OFFLINE
                delay(2_000)
                continue
            }
            try {
                client.prepareGet("api/v1/events/stream") {
                    header(HttpHeaders.Accept, "text/event-stream")
                    lastId?.let { header("Last-Event-ID", it) }
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = socketTimeoutMs
                    }
                }.execute { res ->
                    when (res.status.value) {
                        200 -> Unit
                        401 -> { rejectedToken = token; onUnauthorized(); return@execute }
                        else -> return@execute
                    }
                    val channel = res.bodyAsChannel()
                    val parser = SseParser()
                    while (true) {
                        val line = channel.readUTF8Line() ?: break
                        val frame = parser.feed(line) ?: continue
                        frame.id?.let { lastId = it }
                        val event = runCatching { LogyxJson.decodeFromString(RealtimeEvent.serializer(), frame.data) }
                            .getOrNull() ?: continue
                        when (event.type) {
                            RealtimeTypes.STREAM_READY, RealtimeTypes.STREAM_RESYNC -> {
                                val resync = everLive || event.type == RealtimeTypes.STREAM_RESYNC
                                everLive = true
                                backoff = 1_000L
                                _status.value = RealtimeStatus.LIVE
                                if (resync) send(event.copy(type = RealtimeTypes.STREAM_RESYNC))
                            }
                            RealtimeTypes.STREAM_EXPIRED -> break
                            else -> send(event)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // red caída, timeout del socket o respuesta inválida: se reintenta
            }
            _status.value = if (everLive) RealtimeStatus.RECONNECTING else RealtimeStatus.OFFLINE
            delay(backoff + Random.nextLong(0, 500))
            backoff = (backoff * 2).coerceAtMost(maxBackoffMs)
        }
    }
}
