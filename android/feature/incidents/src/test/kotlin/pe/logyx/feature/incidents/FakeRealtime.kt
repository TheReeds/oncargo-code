package pe.logyx.feature.incidents

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.model.RealtimeEvent
import pe.logyx.core.model.RealtimeStatus

class FakeRealtime(initial: RealtimeStatus = RealtimeStatus.LIVE) : RealtimeRepository {
    val flow = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 16)
    val statusFlow = MutableStateFlow(initial)
    override val events: SharedFlow<RealtimeEvent> = flow
    override val status: StateFlow<RealtimeStatus> = statusFlow

    suspend fun emit(type: String, vararg data: Pair<String, String>) =
        flow.emit(RealtimeEvent(type, data = JsonObject(data.associate { it.first to JsonPrimitive(it.second) })))
}
