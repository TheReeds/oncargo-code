package pe.logyx.feature.driver

import pe.logyx.core.data.RoutesRepository
import pe.logyx.core.data.between
import pe.logyx.core.model.RouteGeometry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.DriverActionQueue
import pe.logyx.core.data.DriverTripCache
import pe.logyx.core.data.FlushResult
import pe.logyx.core.data.PendingStopAction
import pe.logyx.core.data.QueueStall
import pe.logyx.core.data.StopActionOp
import pe.logyx.core.data.waitsForPhoto
import pe.logyx.core.data.withPending
import pe.logyx.core.network.ApiException
import pe.logyx.core.model.TrackingErrors
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.data.apiCode
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.userMessage
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.RealtimeTypes
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

/** Cuándo se puede subir evidencia de entrega: `OPEN` entre la llegada y el cierre; `NOT_YET` justo antes de llegar; `CLOSED` ya entregado/cancelado. */
enum class EvidencePhase { NONE, NOT_YET, OPEN, CLOSED }

/** Resultado de avanzar; la pantalla lo traduce a un mensaje. `QUEUED` = guardado en el teléfono, sale al recuperar señal. */
enum class DriverNotice { STOP_UPDATED, DELIVERED, QUEUED }

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
    /** Viaje mostrado: lo último del servidor (o del disco sin red) con las acciones pendientes aplicadas de forma optimista. */
    val trip: ShipmentView? = null,
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Ruta por carretera para el mapa (ADR-0008); null = cargando o sin red (el mapa dibuja la recta). */
    val route: RouteGeometry? = null,
    /** Se está enviando el toque recién hecho (máx. unos segundos; después sigue en la cola): el botón muestra carga. */
    val acting: Boolean = false,
    /** Error al avanzar el viaje (se muestra sobre el botón, sin perder el viaje en pantalla). */
    val actionError: String? = null,
    /** Resultado de la última acción (se muestra una vez con MessageEffect). */
    val notice: DriverNotice? = null,
    /** Toda la evidencia conocida (de todos los envíos); [evidence] la filtra al envío y parada actuales. */
    val allEvidence: List<EvidenceItem> = emptyList(),
    /** El backend rechazó `complete` por falta de foto (LOGYX-TRK-006). */
    val photoMissingRejected: Boolean = false,
    /** El backend rechazó avanzar por incidencia activa (LOGYX-TRK-005). */
    val incidentRejected: Boolean = false,
    /** Viajes anteriores (entregados y cancelados, recientes primero; `scope=HISTORY`, V15 F22). Vacío si no hay o no cargó. */
    val history: List<ShipmentView> = emptyList(),
    /** La última consulta no llegó al servidor (sin red): se muestran los datos de [cachedAt]. */
    val offline: Boolean = false,
    /** Hora (ISO) en que se guardaron los datos que se ven sin conexión. */
    val cachedAt: String? = null,
    /** Llegadas/cierres de este viaje guardados en el teléfono que el servidor aún no confirmó (roadmap 1.11). */
    val pendingActions: List<PendingStopAction> = emptyList(),
    /** Por qué la cola no se vació en el último intento (null = no se intentó o se vació). */
    val queueStall: QueueStall? = null,
) {
    val action: DriverAction? get() = trip?.driverAction()
    val delivered: Boolean get() = trip?.status == ShipmentStatus.DELIVERED

    /** Hay acciones del conductor que todavía no llegaron al servidor: «Se enviará al recuperar señal». */
    val pendingSync: Boolean get() = pendingActions.isNotEmpty()

    /** Evidencia (foto/firma) del envío y la parada de entrega actuales. */
    val evidence: List<EvidenceItem>
        get() {
            val t = trip ?: return emptyList()
            val stopId = action?.stop?.id
            return allEvidence.filter { it.shipmentId == t.id && (stopId == null || it.stopId == null || it.stopId == stopId) }
        }

    /** Pide evidencia: estamos en el paso de entrega. */
    val needsEvidence: Boolean get() = action?.step == DriverStep.DELIVERED

    /**
     * RN06/F14: la evidencia (foto y firma) solo se sube entre la **llegada** y el **cierre** de la parada de entrega.
     * Antes de llegar y tras cerrar el backend la rechaza (`DOC-005`/`DOC-008`), así que la UI no la ofrece y lo explica.
     */
    val evidencePhase: EvidencePhase
        get() = when {
            trip == null -> EvidencePhase.NONE
            needsEvidence -> EvidencePhase.OPEN
            action?.step == DriverStep.ARRIVE_DESTINATION -> EvidencePhase.NOT_YET
            trip.status == ShipmentStatus.DELIVERED || trip.status == ShipmentStatus.CANCELLED -> EvidencePhase.CLOSED
            else -> EvidencePhase.NONE
        }

    /** Evidencias que el servidor ya no aceptó (`DOC-008`/`DOC-007`): se descartaron y hay que avisar al conductor. */
    val closedEvidence: List<EvidenceItem>
        get() = allEvidence.filter { it.state == EvidenceState.CLOSED } // de cualquier envío: tras entregar ya no hay viaje activo

    /** Hay al menos una foto de entrega ya subida (RN06). */
    val hasUploadedPhoto: Boolean get() = evidence.any { it.type == DocumentType.DELIVERY_PHOTO && it.state == EvidenceState.UPLOADED }

    /** Foto de entrega guardada en el teléfono esperando señal: basta para tocar «Entregado» (el cierre sale después de la foto). */
    val hasPhotoAwaitingSignal: Boolean get() = evidence.any { it.type == DocumentType.DELIVERY_PHOTO && it.awaitingSignal }

    /** Incidencia activa (RN11): bloquea arrive/complete. */
    val incidentBlocked: Boolean get() = trip?.hasActiveIncident == true || incidentRejected

    /** RN06 en la UI: sin foto subida (o en cola por falta de señal) no se puede marcar la entrega. */
    val photoBlocked: Boolean get() = needsEvidence && !hasUploadedPhoto && !hasPhotoAwaitingSignal

    /** El botón XL puede pulsarse. */
    val canAdvance: Boolean get() = action != null && !acting && !incidentBlocked && !photoBlocked
}

private const val RETRY_INTERVAL_MS = 20_000L
private const val HISTORY_LIMIT = 10

/** Cuánto espera el botón la respuesta del toque antes de soltarlo (el toque ya quedó en la cola). */
private const val ACTING_MAX_MS = 4_000L

/**
 * Viaje activo del conductor con soporte sin señal (roadmap 1.11):
 * - Cada respuesta del servidor se guarda en [cache]; sin red se muestra lo guardado con «Sin conexión · datos de hh:mm».
 * - Cada toque del botón XL entra en [queue] (persistente, con la hora del dispositivo), se aplica al viaje en pantalla al
 *   instante y se envía en orden; sin red se reintenta cada ~20 s, al volver a primer plano, al refrescar y al reconectar
 *   el tiempo real. El cierre de la entrega sale después de que suba la foto.
 */
class DriverTripViewModel(
    private val repo: ShipmentsRepository,
    private val catalog: CatalogRepository,
    private val evidence: EvidenceRepository,
    private val realtime: RealtimeRepository,
    private val routes: RoutesRepository = RoutesRepository.NONE,
    private val cache: DriverTripCache = DriverTripCache.inMemory(),
    private val queue: DriverActionQueue = DriverActionQueue.inMemory(repo, { evidence.items.value }),
) : ViewModel() {
    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    private var retryJob: Job? = null
    private var photoFlush: Job? = null

    /** Último viaje del servidor (o del disco) SIN las acciones pendientes; lo que se ve es `base.withPending(cola)`. */
    private var base: ShipmentView? = null

    /** Hora en que se guardó [base] (para el aviso sin conexión). */
    private var baseSavedAt: String? = null

    init {
        viewModelScope.launch {
            queue.restore()
            recompute()
            fetch(initial = true)
        }
        viewModelScope.launch {
            evidence.items.collect { items ->
                _state.update { it.copy(allEvidence = items) }
                updateRetryLoop()
                // La foto terminó de subir: el cierre de entrega que la esperaba ya puede salir.
                if (queue.pending.value.any { it.needsPhoto && !it.waitsForPhoto(items) } && photoFlush?.isActive != true) {
                    photoFlush = launch { flushQueue() }
                }
            }
        }
        viewModelScope.launch {
            queue.pending.collect { recompute(); updateRetryLoop() }
        }
    }

    /** Pull-to-refresh: primero envía lo pendiente y luego consulta. */
    fun refresh() {
        _state.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch { sync(silent = false) }
    }

    /**
     * Tiempo real (solo en primer plano, lo lanza la UI). El conductor solo recibe eventos de SUS envíos, así que
     * cualquier `shipment.*`/`incident.*` refresca el viaje en silencio (`driver_assigned` puede traer un envío nuevo).
     * Tras reconectar (`stream.resync`) también se envía la cola. Mientras avanza una parada no se pisa el estado.
     */
    suspend fun liveLoop() {
        realtime.refreshSignals(RealtimeTypes.SHIPMENT).collect { if (!_state.value.acting) sync(silent = true) }
    }

    /** Al volver a primer plano: envía la cola y refresca viaje y evidencia (si el primer load aún no terminó, no hace nada). */
    fun onResume() {
        if (_state.value.trip != null && !_state.value.refreshing) refresh()
    }

    private suspend fun sync(silent: Boolean) {
        val r = flushQueue()
        // Con un rechazo, flushQueue ya recargó y marcó el motivo (TRK-005/006): otra consulta lo borraría.
        if (r.rejection == null) fetch(initial = false, silent = silent)
        else _state.update { it.copy(refreshing = false) }
    }

    /** Recalcula el viaje mostrado con la cola actual (optimista). */
    private fun recompute() {
        val pending = queue.pending.value
        _state.update { s ->
            val shown = base?.withPending(pending) ?: s.trip
            s.copy(trip = shown, pendingActions = pending.filter { it.shipmentId == shown?.id })
        }
    }

    /** Reintenta cada ~20 s las evidencias que fallaron o quedaron en cola y la cola de acciones, mientras haya algo. */
    private fun updateRetryLoop() {
        // No se cancela al quedar sin trabajo: el propio bucle termina en su siguiente vuelta (cancelarlo a mitad de un envío
        // dejaría sin actualizar el viaje).
        if (!hasPendingWork() || retryJob?.isActive == true) return
        retryJob = viewModelScope.launch {
            while (true) {
                delay(RETRY_INTERVAL_MS)
                if (!hasPendingWork()) break
                val id = _state.value.trip?.id
                if (id != null && evidencePending(id)) evidence.retryAll(id)
                if (queue.pending.value.isNotEmpty()) {
                    val r = flushQueue()
                    // Si la cola llegó al servidor, de paso se actualiza el viaje (y se quita el aviso sin conexión).
                    if (r.stall == null) fetch(initial = false, silent = true)
                }
            }
        }
    }

    private fun evidencePending(shipmentId: String) = _state.value.allEvidence.any {
        it.shipmentId == shipmentId && (it.state == EvidenceState.FAILED || it.state == EvidenceState.QUEUED)
    }

    private fun hasPendingWork(): Boolean {
        val id = _state.value.trip?.id
        return (id != null && evidencePending(id)) || queue.pending.value.isNotEmpty()
    }

    private suspend fun fetch(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.trip == null, refreshing = !initial, error = null) }
        repo.driverActive().fold(
            onSuccess = { trip ->
                base = trip
                baseSavedAt = runCatching { cache.save(trip) }.getOrNull()
                val (origin, destination) = geo(trip)
                val pending = queue.pending.value
                _state.update {
                    val shown = trip?.withPending(pending)
                    it.copy(
                        loading = false, refreshing = false, trip = shown, pendingActions = pending.filter { a -> a.shipmentId == trip?.id },
                        origin = origin, destination = destination, actionError = null,
                        incidentRejected = false, offline = false, cachedAt = null, error = null,
                    )
                }
                afterTripLoaded(trip)
                loadHistory()
            },
            onFailure = { e ->
                if ((e as? ApiException)?.isNetwork == true) showOffline(e)
                else if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) }
            },
        )
    }

    /** Sin red: se queda con lo que hay en pantalla o, si no hay nada, con el último viaje guardado en el teléfono. */
    private suspend fun showOffline(e: Throwable) {
        if (base == null) {
            val cached = runCatching { cache.load() }.getOrNull()
            if (cached == null) {
                _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage(), offline = true) }
                return
            }
            base = cached.trip
            baseSavedAt = cached.savedAt
            val (origin, destination) = geo(cached.trip)
            _state.update { it.copy(origin = origin, destination = destination) }
            recompute()
            afterTripLoaded(cached.trip)
        }
        _state.update { it.copy(loading = false, refreshing = false, error = null, offline = true, cachedAt = baseSavedAt) }
    }

    private suspend fun geo(trip: ShipmentView?): Pair<GeoPoint?, GeoPoint?> {
        if (trip == null) return null to null
        val cities = catalog.cities().getOrDefault(emptyList())
        fun point(code: String?) = code?.let { c -> cities.firstOrNull { normalizeCity(it.code) == normalizeCity(c) }?.toGeoPoint() }
        return point(trip.originCity) to point(trip.destinationCity)
    }

    private suspend fun afterTripLoaded(trip: ShipmentView?) {
        if (trip == null) return
        evidence.load(trip.id)
        updateRetryLoop()
        val g = routes.between(trip.originCity, trip.destinationCity)
        if (g != null) _state.update { if (it.trip?.id == trip.id) it.copy(route = g) else it }
    }

    /** "Viajes anteriores" (V15, F22): una llamada barata; si falla, la sección simplemente no aparece. */
    private suspend fun loadHistory() {
        repo.driverHistory().onSuccess { list -> _state.update { it.copy(history = list.take(HISTORY_LIMIT)) } }
    }

    /**
     * Envía la cola. 200 → el envío del servidor pasa a ser la base; error definitivo → se descarta, se recarga del servidor
     * y se explica (`TRK-006` → falta la foto; `TRK-005` → incidencia; el resto, el mensaje del servidor).
     */
    private suspend fun flushQueue(): FlushResult {
        if (queue.pending.value.isEmpty()) return FlushResult()
        val r = queue.flush()
        r.latest?.let { server ->
            if (base == null || base?.id == server.id) {
                base = server
                baseSavedAt = runCatching { cache.save(server) }.getOrNull()
                _state.update { it.copy(offline = false, cachedAt = null) }
            }
        }
        recompute()
        _state.update { it.copy(queueStall = r.stall, offline = if (r.stall == QueueStall.NETWORK) true else it.offline, cachedAt = if (r.stall == QueueStall.NETWORK) baseSavedAt else it.cachedAt) }
        r.rejection?.let { rejection ->
            fetch(initial = false, silent = true)
            when (rejection.error.apiCode) {
                TrackingErrors.MISSING_PHOTO -> _state.update { it.copy(photoMissingRejected = true) } // fetch ya recargó la evidencia
                TrackingErrors.ACTIVE_INCIDENT -> _state.update { it.copy(incidentRejected = true) }
                else -> _state.update { it.copy(actionError = rejection.error.userMessage()) }
            }
        }
        return r
    }

    /**
     * El conductor confirmó la acción actual (arrive/complete): se guarda en la cola con la hora del dispositivo, se ve
     * aplicada al instante y se envía. Sin señal queda pendiente («Se enviará al recuperar señal»).
     */
    fun advance() {
        val s = _state.value
        val trip = s.trip ?: return
        val action = s.action ?: return
        if (!s.canAdvance) return
        _state.update { it.copy(acting = true, actionError = null, photoMissingRejected = false) }
        viewModelScope.launch {
            val op = if (action.op == StopOp.ARRIVE) StopActionOp.ARRIVE else StopActionOp.COMPLETE
            val queued = queue.enqueue(trip.id, action.stop.id, op, needsPhoto = action.step == DriverStep.DELIVERED)
            recompute()
            val sending = async { flushQueue() }
            val r = withTimeoutOrNull(ACTING_MAX_MS) { sending.await() }
            val stillQueued = queue.pending.value.any { it.id == queued.id }
            val notice = when {
                r?.rejection?.action?.id == queued.id -> null // el error ya se muestra sobre el botón
                r == null || stillQueued -> DriverNotice.QUEUED
                _state.value.delivered -> DriverNotice.DELIVERED
                else -> DriverNotice.STOP_UPDATED
            }
            _state.update { it.copy(acting = false, notice = notice) }
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

    /** El conductor leyó el aviso de evidencia cerrada: se quitan esos ítems de la lista. */
    fun dismissClosedEvidence() {
        val ids = _state.value.closedEvidence.map { it.id }
        viewModelScope.launch { ids.forEach { evidence.discard(it) } }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }
}
