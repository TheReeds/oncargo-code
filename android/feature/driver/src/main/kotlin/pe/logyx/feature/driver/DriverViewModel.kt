package pe.logyx.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.data.apiCode
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView
import pe.logyx.core.model.nextStop
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint

enum class StopOp { ARRIVE, COMPLETE }

/** La única acción que el conductor puede hacer ahora mismo: un botón, un estado. */
data class DriverAction(val stop: StopView, val op: StopOp, val step: DriverStep)

/** Los cuatro pasos del viaje; la pantalla resuelve el texto de cada uno. */
enum class DriverStep { ARRIVE_PICKUP, PICKED_UP, ARRIVE_DESTINATION, DELIVERED }

/** Resultado exitoso de avanzar; la pantalla lo traduce a un mensaje. */
enum class DriverNotice { STOP_UPDATED, DELIVERED }

/**
 * Avanza el viaje: Llegué al recojo → Carga recogida → Llegué a destino → Entregado.
 * Devuelve null si el viaje ya terminó o no tiene paradas.
 */
fun ShipmentView.driverAction(): DriverAction? {
    if (status == ShipmentStatus.DELIVERED || status == ShipmentStatus.CANCELLED) return null
    val stop = nextStop() ?: return null
    val pickup = stop.type == StopType.PICKUP
    return when (stop.status) {
        StopStatus.PENDING -> DriverAction(stop, StopOp.ARRIVE, if (pickup) DriverStep.ARRIVE_PICKUP else DriverStep.ARRIVE_DESTINATION)
        StopStatus.ARRIVED -> DriverAction(stop, StopOp.COMPLETE, if (pickup) DriverStep.PICKED_UP else DriverStep.DELIVERED)
        StopStatus.COMPLETED -> null
    }
}

data class DriverUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val trip: ShipmentView? = null,
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Hay una llamada de parada en curso: el botón se deshabilita. */
    val acting: Boolean = false,
    /** Error al avanzar el viaje (se muestra sobre el botón, sin perder el viaje en pantalla). */
    val actionError: String? = null,
    /** Éxito de la última acción (se muestra una vez con MessageEffect). */
    val notice: DriverNotice? = null,
    /** Toda la evidencia conocida (de todos los envíos); [evidence] la filtra al envío y parada actuales. */
    val allEvidence: List<EvidenceItem> = emptyList(),
    /** El backend rechazó `complete` por falta de foto (LOGYX-TRK-006). */
    val photoMissingRejected: Boolean = false,
    /** El backend rechazó avanzar por incidencia activa (LOGYX-TRK-005). */
    val incidentRejected: Boolean = false,
) {
    val action: DriverAction? get() = trip?.driverAction()
    val delivered: Boolean get() = trip?.status == ShipmentStatus.DELIVERED

    /** Evidencia (foto/firma) del envío y la parada de entrega actuales. */
    val evidence: List<EvidenceItem>
        get() {
            val t = trip ?: return emptyList()
            val stopId = action?.stop?.id
            return allEvidence.filter { it.shipmentId == t.id && (stopId == null || it.stopId == null || it.stopId == stopId) }
        }

    /** Pide evidencia: estamos en el paso de entrega. */
    val needsEvidence: Boolean get() = action?.step == DriverStep.DELIVERED

    /** Hay al menos una foto de entrega ya subida (RN06). */
    val hasUploadedPhoto: Boolean get() = evidence.any { it.type == DocumentType.DELIVERY_PHOTO && it.state == EvidenceState.UPLOADED }

    /** Incidencia activa (RN11): bloquea arrive/complete. */
    val incidentBlocked: Boolean get() = trip?.hasActiveIncident == true || incidentRejected

    val photoBlocked: Boolean get() = needsEvidence && !hasUploadedPhoto

    /** El botón XL puede pulsarse. */
    val canAdvance: Boolean get() = action != null && !acting && !incidentBlocked && !photoBlocked
}

private const val RETRY_INTERVAL_MS = 20_000L

class DriverTripViewModel(
    private val repo: ShipmentsRepository,
    private val catalog: CatalogRepository,
    private val evidence: EvidenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    private var retryJob: Job? = null

    init {
        load(initial = true)
        viewModelScope.launch {
            evidence.items.collect { items ->
                _state.update { it.copy(allEvidence = items) }
                updateRetryLoop()
            }
        }
    }

    fun refresh() = load(initial = false)

    /** Al volver a primer plano: refresca viaje y evidencia (si el primer load aún no terminó, no hace nada). */
    fun onResume() {
        if (_state.value.trip != null && !_state.value.refreshing) refresh()
    }

    /** Reintenta cada ~20 s las evidencias que fallaron o quedaron en cola, mientras haya alguna. */
    private fun updateRetryLoop() {
        val shipmentId = _state.value.trip?.id
        val pending = shipmentId != null && _state.value.allEvidence.any {
            it.shipmentId == shipmentId && (it.state == EvidenceState.FAILED || it.state == EvidenceState.QUEUED)
        }
        if (!pending) { retryJob?.cancel(); retryJob = null; return }
        if (retryJob?.isActive == true) return
        retryJob = viewModelScope.launch {
            while (true) {
                delay(RETRY_INTERVAL_MS)
                val id = _state.value.trip?.id ?: break
                val stillPending = _state.value.allEvidence.any {
                    it.shipmentId == id && (it.state == EvidenceState.FAILED || it.state == EvidenceState.QUEUED)
                }
                if (!stillPending) break
                evidence.retryAll(id)
            }
        }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.trip == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.driverActive().fold(
                onSuccess = { trip ->
                    val cities = catalog.cities().getOrDefault(emptyList())
                    fun geo(code: String?) = code?.let { c -> cities.firstOrNull { normalizeCity(it.code) == normalizeCity(c) }?.toGeoPoint() }
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, trip = trip,
                            origin = geo(trip?.originCity), destination = geo(trip?.destinationCity), actionError = null,
                            incidentRejected = false,
                        )
                    }
                    if (trip != null) {
                        evidence.load(trip.id)
                        updateRetryLoop()
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    /** Ejecuta la acción actual (arrive/complete) y reemplaza el viaje con la respuesta del servidor. */
    fun advance() {
        val s = _state.value
        val trip = s.trip ?: return
        val action = s.action ?: return
        if (!s.canAdvance) return
        _state.update { it.copy(acting = true, actionError = null, photoMissingRejected = false) }
        viewModelScope.launch {
            val result = when (action.op) {
                StopOp.ARRIVE -> repo.arrive(trip.id, action.stop.id)
                StopOp.COMPLETE -> repo.complete(trip.id, action.stop.id)
            }
            result.fold(
                onSuccess = { updated ->
                    val notice = if (updated.status == ShipmentStatus.DELIVERED) DriverNotice.DELIVERED else DriverNotice.STOP_UPDATED
                    _state.update { it.copy(acting = false, trip = updated, notice = notice) }
                },
                onFailure = { e ->
                    when (e.apiCode) {
                        "LOGYX-TRK-006" -> {
                            _state.update { it.copy(acting = false, photoMissingRejected = true) }
                            evidence.load(trip.id)
                        }
                        "LOGYX-TRK-005" -> _state.update { it.copy(acting = false, incidentRejected = true) }
                        else -> _state.update { it.copy(acting = false, actionError = e.userMessage()) }
                    }
                },
            )
        }
    }

    /** Foto de entrega capturada: se comprime, se guarda en la cola y se sube. */
    fun onPhotoCaptured(bytes: ByteArray) = enqueue(DocumentType.DELIVERY_PHOTO, bytes, "image/jpeg")

    /** Firma del receptor (PNG). */
    fun onSignatureCaptured(png: ByteArray) = enqueue(DocumentType.SIGNATURE, png, "image/png")

    private fun enqueue(type: DocumentType, bytes: ByteArray, mime: String) {
        val s = _state.value
        val trip = s.trip ?: return
        val stop = s.action?.stop ?: return
        viewModelScope.launch { evidence.enqueue(trip.id, stop.id, type, bytes, mime) }
    }

    fun retryEvidence(id: String) { viewModelScope.launch { evidence.retry(id) } }

    fun discardEvidence(id: String) { viewModelScope.launch { evidence.discard(id) } }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }
}
