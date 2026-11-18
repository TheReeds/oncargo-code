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
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.network.ApiException

const val MAX_DESCRIPTION = 2000

/** `@Size(max = 2000)` del comentario de incidencia (`body`; RN07 aplica a las partes). */
const val MAX_INCIDENT_COMMENT = 2000

/** Foto tomada (bytes JPEG). Clase propia para tener igualdad por identidad en el estado. */
class IncidentPhoto(val bytes: ByteArray)

// ───────────────────────── Reportar ─────────────────────────

data class ReportUiState(
    val category: IncidentCategory? = null,
    val description: String = "",
    val photo: IncidentPhoto? = null,
    val categoryError: UiText? = null,
    val descriptionError: UiText? = null,
    val confirming: Boolean = false,
    val submitting: Boolean = false,
    val message: UiMessage? = null,
    /** Id de la incidencia creada; la UI navega (tras avisar si [photoFailed]). */
    val reportedId: String? = null,
    val photoFailed: Boolean = false,
)

class ReportIncidentViewModel(
    private val shipmentId: String,
    private val incidents: IncidentsRepository,
    private val documents: DocumentsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun onCategory(c: IncidentCategory) = _state.update { it.copy(category = c, categoryError = null) }

    fun onDescription(text: String) {
        if (text.length > MAX_DESCRIPTION + 200) return
        _state.update { it.copy(description = text, descriptionError = null) }
    }

    fun onPhoto(bytes: ByteArray?) = _state.update { it.copy(photo = bytes?.let(::IncidentPhoto)) }

    fun onPhotoError() = _state.update { it.copy(message = UiMessage.error(R.string.incidents_photo_error)) }

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

    fun confirmSubmit() {
        val s = _state.value
        val category = s.category ?: return
        if (s.submitting) return
        _state.update { it.copy(confirming = false, submitting = true) }
        viewModelScope.launch {
            incidents.report(shipmentId, category, s.description).fold(
                onSuccess = { created ->
                    var photoFailed = false
                    s.photo?.let { p ->
                        photoFailed = documents.upload(shipmentId, p.bytes, "incidencia.jpg", "image/jpeg", DocumentType.OTHER).isFailure
                    }
                    _state.update { it.copy(submitting = false, reportedId = created.id, photoFailed = photoFailed) }
                },
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
) {
    val canComment: Boolean get() = incident?.status.let { it == pe.logyx.core.model.IncidentStatus.OPEN || it == pe.logyx.core.model.IncidentStatus.IN_REVIEW }
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

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
