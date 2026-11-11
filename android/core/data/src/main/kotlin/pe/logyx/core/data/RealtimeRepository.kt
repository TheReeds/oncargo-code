package pe.logyx.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import pe.logyx.core.model.RealtimeEvent
import pe.logyx.core.model.RealtimeStatus
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.network.RealtimeClient

/**
 * Tiempo real (ADR-0007). Una sola conexión SSE compartida por toda la app y **solo mientras alguien colecta**
 * [events] (las pantallas colectan con el ciclo de vida: en segundo plano no hay conexión; se cierra 5 s después del
 * último suscriptor).
 */
interface RealtimeRepository {
    val events: SharedFlow<RealtimeEvent>
    val status: StateFlow<RealtimeStatus>
}

class DefaultRealtimeRepository(client: RealtimeClient, scope: CoroutineScope) : RealtimeRepository {
    override val events: SharedFlow<RealtimeEvent> =
        client.events().shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 0)
    override val status: StateFlow<RealtimeStatus> = client.status
}

/**
 * Señales de refresco para una pantalla: cada evento de [types] que cumpla [match], cada `stream.resync` (se
 * reconectó: pudo perderse algo) y, **solo mientras el stream no está en vivo**, un tick de respaldo cada [fallbackMs].
 * Colecta esto en lugar del antiguo bucle de polling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun RealtimeRepository.refreshSignals(
    types: Set<String>,
    fallbackMs: Long = 60_000,
    match: (RealtimeEvent) -> Boolean = { true },
): Flow<RealtimeEvent?> = merge(
    events.filter { it.type == RealtimeTypes.STREAM_RESYNC || (it.type in types && match(it)) },
    status.flatMapLatest { s ->
        if (s == RealtimeStatus.LIVE || fallbackMs <= 0) emptyFlow<RealtimeEvent?>()
        else flow<RealtimeEvent?> { while (true) { delay(fallbackMs); emit(null) } }
    },
)
