package pe.logyx.feature.negotiation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.BubbleState
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.model.MessageView
import pe.logyx.core.network.ApiException

/** Una burbuja de la conversación: mensaje del servidor o mensaje local (optimista / fallido). */
data class ChatEntry(
    val id: String,
    val body: String,
    val mine: Boolean,
    val system: Boolean = false,
    val proposedPrice: Double? = null,
    val createdAt: String? = null,
    val senderName: String? = null,
    val state: BubbleState = BubbleState.Sent,
) {
    val isLocal: Boolean get() = id.startsWith(LOCAL_PREFIX)

    companion object {
        const val LOCAL_PREFIX = "local-"
    }
}

data class ChatUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** Cronológico: mensajes del servidor y, al final, los locales pendientes o fallidos. */
    val entries: List<ChatEntry> = emptyList(),
    val nextCursor: String? = null,
    val loadingOlder: Boolean = false,
    val draft: String = "",
    /** Error RN07 (u otro de campo) mostrado debajo del campo de texto. */
    val composerError: String? = null,
    /** El servidor cerró el hilo: el campo queda deshabilitado y se explica por qué. */
    val readOnlyReason: String? = null,
    val message: UiMessage? = null,
) {
    val readOnly: Boolean get() = readOnlyReason != null
}

class ChatViewModel(
    private val requestId: String,
    private val carrierOrgId: String,
    private val repo: NegotiationRepository,
    private val realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var localSeq = 0

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.entries.isEmpty(), error = null) }
        viewModelScope.launch {
            repo.messages(requestId, carrierOrgId, null).fold(
                onSuccess = { page ->
                    _state.update { s ->
                        s.copy(loading = false, entries = mergeServer(s.entries, page.items), nextCursor = if (s.nextCursor == null || s.entries.none { !it.isLocal }) page.nextCursor else s.nextCursor)
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = if (it.entries.isEmpty()) e.userMessage() else null) } },
            )
        }
    }

    fun loadOlder() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingOlder) return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            repo.messages(requestId, carrierOrgId, cursor).fold(
                onSuccess = { page ->
                    _state.update { it.copy(loadingOlder = false, entries = mergeServer(it.entries, page.items), nextCursor = page.nextCursor) }
                },
                onFailure = { e -> _state.update { it.copy(loadingOlder = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    /** Consulta los mensajes más recientes y los fusiona por id (sin duplicar los optimistas). Errores: silencio. */
    suspend fun pollOnce() {
        repo.messages(requestId, carrierOrgId, null).onSuccess { page ->
            _state.update { s ->
                s.copy(entries = mergeServer(s.entries, page.items), nextCursor = if (s.entries.none { !it.isLocal }) page.nextCursor else s.nextCursor)
            }
        }
    }

    /**
     * Señales de tiempo real; la UI lo ejecuta mientras está en primer plano y se cancela al salir. Refresca con
     * `message.posted` de ESTE hilo (`requestId` + `carrierOrgId`), con `counter_offer.updated` de la solicitud y con
     * `stream.resync`; el polling de respaldo (>= 30 s) solo corre si el stream no está en vivo.
     */
    suspend fun liveLoop(fallbackMs: Long = FALLBACK_INTERVAL_MS) {
        realtime.refreshSignals(setOf(RealtimeTypes.MESSAGE_POSTED, RealtimeTypes.COUNTER_OFFER_UPDATED), fallbackMs) { e ->
            e.string("requestId") == requestId &&
                (e.type != RealtimeTypes.MESSAGE_POSTED || e.string("carrierOrgId") == carrierOrgId)
        }.collect { pollOnce() }
    }

    fun onDraft(text: String) = _state.update { it.copy(draft = text, composerError = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun send() {
        val s = _state.value
        val body = s.draft.trim()
        if (body.isEmpty() || s.readOnly) return
        val local = ChatEntry("${ChatEntry.LOCAL_PREFIX}${++localSeq}", body, mine = true, state = BubbleState.Sending)
        _state.update { it.copy(draft = "", composerError = null, entries = it.entries + local) }
        deliver(local)
    }

    fun retry(localId: String) {
        val entry = _state.value.entries.firstOrNull { it.id == localId && it.state == BubbleState.Failed } ?: return
        val sending = entry.copy(state = BubbleState.Sending)
        _state.update { s -> s.copy(entries = s.entries.map { if (it.id == localId) sending else it }) }
        deliver(sending)
    }

    fun discard(localId: String) = _state.update { s -> s.copy(entries = s.entries.filterNot { it.id == localId }) }

    private fun deliver(local: ChatEntry) {
        viewModelScope.launch {
            repo.send(requestId, carrierOrgId, local.body).fold(
                onSuccess = { real ->
                    _state.update { s ->
                        val without = s.entries.filterNot { it.id == local.id }
                        s.copy(entries = mergeServer(without, listOf(real)))
                    }
                },
                onFailure = { e -> onSendFailed(local, e) },
            )
        }
    }

    private fun onSendFailed(local: ChatEntry, e: Throwable) {
        val api = e as? ApiException
        when {
            // RN07 (u otro error de campo): no es un mensaje enviado; el texto vuelve al campo con la explicación.
            api?.field == "body" || api?.code == "LOGYX-NEG-001" -> _state.update { s ->
                s.copy(
                    entries = s.entries.filterNot { it.id == local.id },
                    draft = if (s.draft.isBlank()) local.body else s.draft,
                    composerError = e.userMessage(),
                )
            }
            // Hilo cerrado (409) o sin permiso (403): solo lectura.
            api != null && (api.status == 409 || api.status == 403) -> _state.update { s ->
                s.copy(
                    entries = s.entries.filterNot { it.id == local.id },
                    draft = if (s.draft.isBlank()) local.body else s.draft,
                    readOnlyReason = e.userMessage(),
                )
            }
            else -> _state.update { s ->
                s.copy(
                    entries = s.entries.map { if (it.id == local.id) it.copy(state = BubbleState.Failed) else it },
                    message = UiMessage.error(e.userMessage()),
                )
            }
        }
    }

    companion object {
        const val FALLBACK_INTERVAL_MS = 30_000L
    }
}

private fun MessageView.toEntry() = ChatEntry(
    id = id, body = body, mine = mine, system = system, proposedPrice = proposedPrice, createdAt = createdAt, senderName = senderName,
)

/** Une los mensajes del servidor (por id, orden cronológico) y deja al final los locales pendientes o fallidos. */
internal fun mergeServer(current: List<ChatEntry>, incoming: List<MessageView>): List<ChatEntry> {
    val server = LinkedHashMap<String, ChatEntry>()
    current.filter { !it.isLocal }.forEach { server[it.id] = it }
    incoming.forEach { server[it.id] = it.toEntry() }
    val sorted = server.values.sortedBy { it.createdAt.orEmpty() }
    return sorted + current.filter { it.isLocal }
}
