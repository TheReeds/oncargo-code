package pe.logyx.feature.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.DocumentsRepository
import pe.logyx.core.data.IncidentsRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentView
import pe.logyx.core.model.RealtimeEvent
import pe.logyx.core.model.ReportIncidentRequest
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.network.ApiException
import java.util.UUID

const val MAX_DESCRIPTION = 2000

/** `@Size(max = 2000)` del comentario de incidencia (`body`; RN07 aplica a las partes). */
const val MAX_INCIDENT_COMMENT = 2000

/** Máximo de adjuntos por incidencia (`attachmentIds`, contrato V23). */
const val MAX_ATTACHMENTS = 5

/** Tamaño máximo de un adjunto (como los documentos del envío). */
const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

/** Formatos que acepta el servidor para los documentos del envío. */
val ATTACHMENT_MIME_TYPES = arrayOf("image/jpeg", "image/png", "application/pdf")

/** Archivo elegido para adjuntar (foto de la cámara o JPG/PNG/PDF). Clase propia: igualdad por identidad en el estado. */
class IncidentAttachment(val id: Int, val bytes: ByteArray, val fileName: String, val mime: String) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val isPdf: Boolean get() = mime == "application/pdf"
}

// ───────────────────────── Reportar ─────────────────────────

data class ReportUiState(
    val category: IncidentCategory? = null,
    val description: String = "",
    val attachments: List<IncidentAttachment> = emptyList(),
    val categoryError: UiText? = null,
    val descriptionError: UiText? = null,
    val confirming: Boolean = false,
    val submitting: Boolean = false,
    val message: UiMessage? = null,
    /** Id de la incidencia creada; la UI navega (tras avisar si [attachFailed] > 0). */
    val reportedId: String? = null,
    /** Cuántos adjuntos no se pudieron subir: la incidencia se reportó sin ellos. */
    val attachFailed: Int = 0,
) {
    val canAttachMore: Boolean get() = attachments.size < MAX_ATTACHMENTS
}

class ReportIncidentViewModel(
    private val shipmentId: String,
    private val incidents: IncidentsRepository,
    private val documents: DocumentsRepository,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private var nextAttachmentId = 0

    /** Adjuntos ya subidos como documento del envío (por id de adjunto): un reintento del reporte no los sube otra vez. */
    private val uploaded = mutableMapOf<Int, String>()

    /** Cuerpo y clave del último reporte enviado: si falló (red) y se reintenta igual, sale con la misma `Idempotency-Key`. */
    private var lastReport: Pair<ReportIncidentRequest, String>? = null

    fun onCategory(c: IncidentCategory) = _state.update { it.copy(category = c, categoryError = null) }

    fun onDescription(text: String) {
        if (text.length > MAX_DESCRIPTION + 200) return
        _state.update { it.copy(description = text, descriptionError = null) }
    }

    /**
     * Agrega un adjunto (foto de la cámara o archivo elegido): valida cantidad (5), formato (JPG, PNG o PDF) y tamaño (10 MB).
     * Devuelve si quedó agregado; si no, deja el aviso.
     */
    fun addAttachment(bytes: ByteArray?, fileName: String, mime: String?): Boolean {
        val error = when {
            _state.value.submitting -> return false
            !_state.value.canAttachMore -> R.string.incidents_attach_limit
            bytes == null || bytes.isEmpty() -> R.string.incidents_attach_unreadable
            mime == null || mime !in ATTACHMENT_MIME_TYPES -> R.string.incidents_attach_format
            bytes.size > MAX_ATTACHMENT_BYTES -> R.string.incidents_attach_too_big
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(message = UiMessage.error(error)) }
            return false
        }
        val item = IncidentAttachment(nextAttachmentId++, bytes!!, fileName, mime!!)
        _state.update { it.copy(attachments = it.attachments + item) }
        return true
    }

    fun removeAttachment(id: Int) {
        if (_state.value.submitting) return
        uploaded.remove(id)
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }
    }

    /** No se pudo abrir la cámara o el selector. */
    fun onAttachError() = _state.update { it.copy(message = UiMessage.error(R.string.incidents_photo_error)) }

    /** Valida y abre el diálogo de confirmación (congela el envío, RN11). */
    fun askSubmit() {
        val s = _state.value
        val catErr = if (s.category == null) UiText.of(R.string.incidents_category_required) else null
        val descErr = when {
            s.description.isBlank() -> UiText.of(R.string.incidents_description_required)
            s.description.trim().length > MAX_DESCRIPTION -> UiText.of(R.string.incidents_description_too_long, MAX_DESCRIPTION)
            else -> null
        }
        if (catErr != null || descErr != null) {
            _state.update { it.copy(categoryError = catErr, descriptionError = descErr) }
        } else {
            _state.update { it.copy(confirming = true) }
        }
    }

    fun dismissConfirm() = _state.update { it.copy(confirming = false) }

    /**
     * Sube cada adjunto como documento del envío (`OTHER`), junta los ids y reporta con `attachmentIds`. Un adjunto que no
     * sube no bloquea el reporte: se reporta sin él y se avisa ([ReportUiState.attachFailed]).
     */
    fun confirmSubmit() {
        val s = _state.value
        val category = s.category ?: return
        if (s.submitting) return
        _state.update { it.copy(confirming = false, submitting = true) }
        viewModelScope.launch {
            var failed = 0
            val ids = mutableListOf<String>()
            s.attachments.forEach { a ->
                val known = uploaded[a.id]
                if (known != null) { ids += known; return@forEach }
                documents.upload(shipmentId, a.bytes, a.fileName, a.mime, DocumentType.OTHER).fold(
                    onSuccess = { doc -> uploaded[a.id] = doc.id; ids += doc.id },
                    onFailure = { failed++ },
                )
            }
            val request = ReportIncidentRequest(
                category = category, description = s.description, attachmentIds = ids.takeIf { it.isNotEmpty() },
            )
            val key = lastReport?.takeIf { it.first == request }?.second ?: newKey()
            lastReport = request to key
            incidents.report(shipmentId, request, key).fold(
                onSuccess = { created -> _state.update { it.copy(submitting = false, reportedId = created.id, attachFailed = failed) } },
                onFailure = { e -> _state.update { it.copy(submitting = false).withError(e) } },
            )
        }
    }

    private fun ReportUiState.withError(e: Throwable): ReportUiState {
        val api = e as? ApiException
        return when (api?.field) {
            "description" -> copy(descriptionError = UiText.Raw(api.message))
            "category" -> copy(categoryError = UiText.Raw(api.message))
            else -> copy(message = UiMessage.error(e.userMessage()))
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}

// ───────────────────────── Lista ─────────────────────────

data class IncidentsListUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<IncidentView> = emptyList(),
) {
    val hasActive: Boolean get() = items.any { it.active }

    /** Alguna activa congela el envío (RN11). Las informativas (`blocking = false`) no detienen el viaje. */
    val holdsShipment: Boolean get() = items.any { it.holdsShipment || (it.active && it.blocking) }
}

class IncidentsListViewModel(
    private val shipmentId: String,
    private val repo: IncidentsRepository,
    private val realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IncidentsListUiState())
    val state: StateFlow<IncidentsListUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano): `incident.*` de este envío refresca la lista en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(INCIDENT_TYPES) { it.string("shipmentId") == shipmentId }
            .collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.forShipment(shipmentId).fold(
                onSuccess = { list ->
                    _state.update { it.copy(loading = false, refreshing = false, items = list.sortedByDescending { i -> i.createdAt.orEmpty() }) }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

private val INCIDENT_TYPES = setOf(RealtimeTypes.INCIDENT_REPORTED, RealtimeTypes.INCIDENT_UPDATED)

/** Los comentarios no emiten evento: el detalle conserva un refresco lento (30 s) además de `incident.*`. */
const val INCIDENT_COMMENTS_POLL_MS = 30_000L

// ───────────────────────── Detalle ─────────────────────────

data class IncidentDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val incident: IncidentView? = null,
    /** Organización del usuario: define qué burbujas son "mías". */
    val organizationId: String? = null,
    val draft: String = "",
    val sending: Boolean = false,
    val commentError: String? = null,
    val message: UiMessage? = null,
    /** Diálogo «¿Retirar esta incidencia?» abierto. */
    val confirmingWithdraw: Boolean = false,
    val withdrawing: Boolean = false,
) {
    val canComment: Boolean get() = incident?.status.let { it == pe.logyx.core.model.IncidentStatus.OPEN || it == pe.logyx.core.model.IncidentStatus.IN_REVIEW }

    /** V23: quien la reportó retira una informativa activa (lo decide el servidor con `canWithdraw`). */
    val canWithdraw: Boolean get() = incident?.canWithdraw == true
}

class IncidentDetailViewModel(
    private val incidentId: String,
    private val repo: IncidentsRepository,
    private val auth: AuthRepository,
    private val realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IncidentDetailUiState(organizationId = orgId()))
    val state: StateFlow<IncidentDetailUiState> = _state.asStateFlow()

    private fun orgId() = (auth.state.value as? SessionState.LoggedIn)?.user?.organizationId

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /**
     * Tiempo real (solo en primer plano): `incident.*` de ESTA incidencia refresca el detalle en silencio. Los
     * comentarios no tienen evento, así que además hay un refresco lento cada [INCIDENT_COMMENTS_POLL_MS].
     */
    suspend fun liveLoop(commentsPollMs: Long = INCIDENT_COMMENTS_POLL_MS) {
        merge(
            realtime.refreshSignals(INCIDENT_TYPES, fallbackMs = 0) { it.string("incidentId") == incidentId },
            flow<RealtimeEvent?> { while (true) { delay(commentsPollMs); emit(null) } },
        ).collect { poll() }
    }

    /** Refresco silencioso: no muestra spinner ni errores si ya hay datos. */
    fun poll() {
        if (_state.value.incident == null || _state.value.sending) return
        viewModelScope.launch {
            repo.detail(incidentId).onSuccess { i -> _state.update { it.copy(incident = i) } }
        }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.incident == null, refreshing = !initial, error = null, organizationId = orgId()) }
        viewModelScope.launch {
            repo.detail(incidentId).fold(
                onSuccess = { i -> _state.update { it.copy(loading = false, refreshing = false, incident = i) } },
                onFailure = { e ->
                    _state.update {
                        if (it.incident != null) it.copy(loading = false, refreshing = false, message = UiMessage.error(e.userMessage()))
                        else it.copy(loading = false, refreshing = false, error = e.userMessage())
                    }
                },
            )
        }
    }

    fun onDraft(text: String) = _state.update { it.copy(draft = text.take(MAX_INCIDENT_COMMENT), commentError = null) }

    fun send() {
        val s = _state.value
        if (s.sending || !s.canComment) return
        if (s.draft.isBlank()) {
            _state.update { it.copy(commentError = null, message = UiMessage.error(R.string.incidents_comment_empty)) }
            return
        }
        _state.update { it.copy(sending = true, commentError = null) }
        viewModelScope.launch {
            repo.comment(incidentId, s.draft).fold(
                onSuccess = { i -> _state.update { it.copy(sending = false, incident = i, draft = "") } },
                onFailure = { e ->
                    val api = e as? ApiException
                    _state.update {
                        if (api?.field == "body") it.copy(sending = false, commentError = api.message)
                        else it.copy(sending = false, message = UiMessage.error(e.userMessage()))
                    }
                },
            )
        }
    }

    // ── Retirar (V23, 2.7) ──

    fun askWithdraw() = _state.update { if (it.canWithdraw) it.copy(confirmingWithdraw = true) else it }

    fun dismissWithdraw() = _state.update { if (it.withdrawing) it else it.copy(confirmingWithdraw = false) }

    /** `POST /incidents/{id}/withdraw`; `INC-007` (no la reportaste) u otro motivo llegan como mensaje y se recarga. */
    fun withdraw() {
        if (_state.value.withdrawing || !_state.value.canWithdraw) return
        _state.update { it.copy(withdrawing = true) }
        viewModelScope.launch {
            repo.withdraw(incidentId).fold(
                onSuccess = { i ->
                    _state.update { it.copy(withdrawing = false, confirmingWithdraw = false, incident = i, message = UiMessage.success(R.string.incidents_withdrawn)) }
                },
                onFailure = { e ->
                    _state.update { it.copy(withdrawing = false, confirmingWithdraw = false, message = UiMessage.error(e.userMessage())) }
                    poll()
                },
            )
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
