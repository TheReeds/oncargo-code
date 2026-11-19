package pe.logyx.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.DriverActionQueue
import pe.logyx.core.data.DriverTripCache
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.data.FlushResult
import pe.logyx.core.data.OperationKind
import pe.logyx.core.data.PendingStopAction
import pe.logyx.core.data.QueueStall
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.RoutesRepository
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.StopActionOp
import pe.logyx.core.data.SyncItem
import pe.logyx.core.data.SyncItemState
import pe.logyx.core.data.SyncStatus
import pe.logyx.core.data.apiCode
import pe.logyx.core.data.between
import pe.logyx.core.data.isoNow
import pe.logyx.core.data.pendingUpload
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.syncItems
import pe.logyx.core.data.userMessage
import pe.logyx.core.data.waitsForEvidence
import pe.logyx.core.data.withPending
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentSeverity
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.model.ReceiverDoc
import pe.logyx.core.model.ReportIncidentRequest
import pe.logyx.core.model.RouteGeometry
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView
import pe.logyx.core.model.TrackingErrors
import pe.logyx.core.model.isValidLoadedWeight
import pe.logyx.core.model.isValidReceiverName
import pe.logyx.core.model.nextStop
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.parseWeightKg
import pe.logyx.core.model.toGeoPoint
import pe.logyx.core.network.ApiException
import java.time.Clock

enum class StopOp { ARRIVE, COMPLETE }

/** La única acción que el conductor puede hacer ahora mismo: un botón, un estado. */
data class DriverAction(val stop: StopView, val op: StopOp, val step: DriverStep)

/** Los cuatro pasos del viaje; la pantalla resuelve el texto de cada uno. */
enum class DriverStep { ARRIVE_PICKUP, PICKED_UP, ARRIVE_DESTINATION, DELIVERED }

/** Cuándo se puede subir evidencia de entrega: `OPEN` entre la llegada y el cierre; `NOT_YET` justo antes de llegar; `CLOSED` ya entregado/cancelado. */
enum class EvidencePhase { NONE, NOT_YET, OPEN, CLOSED }

/**
 * Resultado de una acción; la pantalla lo traduce a un mensaje. `QUEUED` = guardado en el teléfono, sale al recuperar señal.
 * `EMERGENCY_SENT`/`EMERGENCY_QUEUED`: el reporte de emergencia llegó o quedó en la cola.
 */
enum class DriverNotice { STOP_UPDATED, DELIVERED, QUEUED, EMERGENCY_SENT, EMERGENCY_QUEUED, SYNCED }

/** Botón «Emergencia»: categoría de la incidencia y severidad (accidente y robo, `CRITICAL`; avería, la de la categoría). */
enum class EmergencyKind(val category: IncidentCategory, val severity: IncidentSeverity?, val defaultDescription: String) {
    ACCIDENT(IncidentCategory.ACCIDENT, IncidentSeverity.CRITICAL, "Emergencia reportada por el conductor desde la app: accidente."),
    THEFT(IncidentCategory.THEFT, IncidentSeverity.CRITICAL, "Emergencia reportada por el conductor desde la app: robo."),
    BREAKDOWN(IncidentCategory.BREAKDOWN, null, "Emergencia reportada por el conductor desde la app: avería del vehículo."),
}

/** Punto del dispositivo (botón «Emergencia»); null si no hay permiso o no se obtuvo a tiempo. */
data class DeviceLocation(val latitude: Double, val longitude: Double)

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

/**
 * Datos que el conductor escribe en la parada actual (2.5): peso medido del recojo y quién recibe en la entrega. Se guardan
 * por parada ([stopId]); al cambiar de parada se empieza en blanco.
 */
data class StopForm(
    val stopId: String? = null,
    val weight: String = "",
    val weightError: UiText? = null,
    val receiverName: String = "",
    val receiverDoc: String = "",
    /** Error del servidor (`TRK-011`) o local tras intentar con datos inválidos. */
    val receiverNameError: UiText? = null,
    val receiverDocError: UiText? = null,
)

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
    /** El backend rechazó `complete` por falta de foto (LOGYX-TRK-006 en la entrega, TRK-009 en el recojo). */
    val photoMissingRejected: Boolean = false,
    /** El backend rechazó avanzar por incidencia activa (LOGYX-TRK-005). */
    val incidentRejected: Boolean = false,
    /** Viajes anteriores (entregados y cancelados, recientes primero; `scope=HISTORY`, V15 F22). Vacío si no hay o no cargó. */
    val history: List<ShipmentView> = emptyList(),
    /** La última consulta no llegó al servidor (sin red): se muestran los datos de [cachedAt]. */
    val offline: Boolean = false,
    /** Hora (ISO) en que se guardaron los datos que se ven sin conexión. */
    val cachedAt: String? = null,
    /** Operaciones de este viaje guardadas en el teléfono que el servidor aún no confirmó (roadmap 1.11 y 2.5). */
    val pendingActions: List<PendingStopAction> = emptyList(),
    /** Por qué la cola no se vació en el último intento (null = no se intentó o se vació). */
    val queueStall: QueueStall? = null,
    /** Estado de la cola completa (todas las operaciones de este usuario, para el indicador y su hoja). */
    val sync: SyncStatus = SyncStatus(),
    /** «Reintentar ahora» en curso. */
    val retrying: Boolean = false,
    /** Peso y receptor de la parada actual. */
    val form: StopForm = StopForm(),
    /** Enviando (o guardando) el reporte de emergencia. */
    val emergencySending: Boolean = false,
) {
    val action: DriverAction? get() = trip?.driverAction()
    val delivered: Boolean get() = trip?.status == ShipmentStatus.DELIVERED

    /** Hay acciones del conductor que todavía no llegaron al servidor: «Se enviará al recuperar señal». */
    val pendingSync: Boolean get() = pendingActions.isNotEmpty()

    /** Lo que falta sincronizar (operaciones + evidencia local), en orden de envío: «3 acciones por sincronizar». */
    val syncList: List<SyncItem> get() = syncItems(sync, allEvidence)

    val syncCount: Int get() = syncList.size

    /** Resumen para el indicador: algo falló › se está enviando › esperando señal › en cola. */
    val syncState: SyncItemState
        get() {
            val list = syncList
            return when {
                list.any { it.state == SyncItemState.FAILED } -> SyncItemState.FAILED
                list.any { it.state == SyncItemState.SENDING } -> SyncItemState.SENDING
                sync.stall == QueueStall.NETWORK || list.any { it.state == SyncItemState.WAITING_SIGNAL } -> SyncItemState.WAITING_SIGNAL
                else -> SyncItemState.QUEUED
            }
        }

    /** Evidencia (foto/firma/guía) del envío y la parada actuales. */
    val evidence: List<EvidenceItem>
        get() {
            val t = trip ?: return emptyList()
            val stopId = action?.stop?.id
            return allEvidence.filter { it.shipmentId == t.id && (stopId == null || it.stopId == null || it.stopId == stopId) }
        }

    /** Pide evidencia: estamos en el paso de entrega. */
    val needsEvidence: Boolean get() = action?.step == DriverStep.DELIVERED

    /** Estamos en el recojo, entre «Llegué» y «Carga recogida»: foto de la carga, peso y guía (2.5). */
    val pickupStep: Boolean get() = action?.step == DriverStep.PICKED_UP

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

    /** Evidencias que el servidor ya no aceptó (`DOC-008`/`DOC-007`/`DOC-009`): se descartaron y hay que avisar al conductor. */
    val closedEvidence: List<EvidenceItem>
        get() = allEvidence.filter { it.state == EvidenceState.CLOSED } // de cualquier envío: tras entregar ya no hay viaje activo

    private fun counts(type: DocumentType, e: EvidenceItem) = e.type == type && (e.state == EvidenceState.UPLOADED || e.awaitingSignal)

    /** Hay al menos una foto de entrega ya subida (RN06). */
    val hasUploadedPhoto: Boolean get() = evidence.any { it.type == DocumentType.DELIVERY_PHOTO && it.state == EvidenceState.UPLOADED }

    /** Foto de entrega guardada en el teléfono esperando señal: basta para tocar «Entregado» (el cierre sale después de la foto). */
    val hasPhotoAwaitingSignal: Boolean get() = evidence.any { it.type == DocumentType.DELIVERY_PHOTO && it.awaitingSignal }

    /** Foto de la carga tomada (subida o en cola por falta de señal; una que falló por el servidor no cuenta). */
    val hasPickupPhoto: Boolean get() = evidence.any { counts(DocumentType.PICKUP_PHOTO, it) }

    val pickupPhotoRequired: Boolean get() = trip?.pickupPhotoRequired == true
    val pickupWeightRequired: Boolean get() = trip?.pickupWeightRequired == true

    /** Peso escrito (kg) o null si está vacío o no es un número. */
    val weightKg: Double? get() = parseWeightKg(form.weight)

    /** El campo de peso está bien: vacío y opcional, o un número entre 1 y 60 000 kg. */
    val weightValid: Boolean
        get() = if (form.weight.isBlank()) !pickupWeightRequired else isValidLoadedWeight(weightKg)

    /** «Carga recogida» espera la foto de la carga (si la PYME la pidió) y el peso (si lo pidió o si se escribió mal). */
    val pickupBlocked: Boolean get() = pickupStep && ((pickupPhotoRequired && !hasPickupPhoto) || !weightValid)

    /** Nombre y DNI (8 dígitos) o CE (9–12) de quien recibe. */
    val receiverValid: Boolean get() = isValidReceiverName(form.receiverName) && ReceiverDoc.isValid(form.receiverDoc)

    /** «Entregado» pide quién recibe (2.5). */
    val receiverBlocked: Boolean get() = needsEvidence && !receiverValid

    /** Incidencia activa que congela (RN11): bloquea arrive/complete. */
    val incidentBlocked: Boolean get() = trip?.hasActiveIncident == true || incidentRejected

    /** RN06 en la UI: sin foto subida (o en cola por falta de señal) no se puede marcar la entrega. */
    val photoBlocked: Boolean get() = needsEvidence && !hasUploadedPhoto && !hasPhotoAwaitingSignal

    /** El botón XL puede pulsarse. */
    val canAdvance: Boolean get() = action != null && !acting && !incidentBlocked && !photoBlocked && !pickupBlocked && !receiverBlocked
}

private const val HISTORY_LIMIT = 10

/** Cuánto espera el botón la respuesta del toque antes de soltarlo (el toque ya quedó en la cola). */
private const val ACTING_MAX_MS = 4_000L

/** Cuánto se espera la ubicación del reporte de emergencia antes de enviarlo sin ella. */
private const val LOCATION_MAX_MS = 5_000L

/** `@Size(max = 2000)` de la descripción de la incidencia. */
private const val MAX_EMERGENCY_TEXT = 2000

/**
 * Viaje activo del conductor con soporte sin señal (roadmap 1.11 y 2.5):
 * - Cada respuesta del servidor se guarda en [cache]; sin red se muestra lo guardado con «Sin conexión · datos de hh:mm».
 * - Cada toque del botón XL (con peso o receptor), cada foto y cada emergencia entra en [queue] (persistente, con la hora del
 *   dispositivo), se aplica al viaje en pantalla al instante y se envía en orden. Sin red la cola reintenta sola con espera
 *   exponencial (5 s → 5 min) y en segundo plano; aquí se reintenta ya al volver a primer plano, al refrescar, al reconectar
 *   el tiempo real y con «Reintentar ahora». El cierre de una parada sale después de que suba su evidencia.
 */
class DriverTripViewModel(
    private val repo: ShipmentsRepository,
    private val catalog: CatalogRepository,
    private val evidence: EvidenceRepository,
    private val realtime: RealtimeRepository,
    private val routes: RoutesRepository = RoutesRepository.NONE,
    private val cache: DriverTripCache = DriverTripCache.inMemory(),
    private val queue: DriverActionQueue = DriverActionQueue.inMemory(repo, { evidence.items.value }),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    /** Último viaje del servidor (o del disco) SIN las acciones pendientes; lo que se ve es `base.withPending(cola)`. */
    private var base: ShipmentView? = null

    /** Hora en que se guardó [base] (para el aviso sin conexión). */
    private var baseSavedAt: String? = null

    private var photoFlush: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            queue.restore()
            recompute()
            fetch(initial = true)
        }
        viewModelScope.launch {
            evidence.items.collect { items ->
                _state.update { it.copy(allEvidence = items) }
                // La evidencia de una parada terminó de subir: el cierre que la esperaba ya puede salir.
                val closeReady = queue.pending.value.any { it.op == OperationKind.COMPLETE && !it.waitsForEvidence(items) }
                // Una foto que no subió por falta de señal entra al reintento con espera exponencial de la cola.
                val photoOffline = items.any { it.pendingUpload && it.state == EvidenceState.FAILED && it.offline } &&
                    queue.status.value.stall != QueueStall.NETWORK
                if ((closeReady || photoOffline) && photoFlush?.isActive != true) {
                    photoFlush = launch { flushQueue() }
                }
            }
        }
        viewModelScope.launch { queue.pending.collect { recompute() } }
        viewModelScope.launch { queue.status.collect { s -> _state.update { it.copy(sync = s) } } }
        // Lo que envió el reintento automático de la cola (en este proceso) también actualiza el viaje.
        viewModelScope.launch { queue.results.collect { handleFlush(it) } }
    }

    /** Pull-to-refresh: primero envía lo pendiente (sin esperar la espera exponencial) y luego consulta. */
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
        val r = flushQueue(now = true)
        // Con un rechazo, flushQueue ya recargó y marcó el motivo (TRK-005/006/009/010/011): otra consulta lo borraría.
        if (r.rejection == null) fetch(initial = false, silent = silent)
        else _state.update { it.copy(refreshing = false) }
    }

    /** «Reintentar ahora» de la hoja de pendientes. */
    fun retrySyncNow() {
        if (_state.value.retrying) return
        _state.update { it.copy(retrying = true) }
        viewModelScope.launch {
            val r = flushQueue(now = true)
            if (r.stall == null && r.rejection == null) fetch(initial = false, silent = true)
            // Directo de la cola (el estado de la pantalla puede no haber recibido aún el último `status`).
            val left = syncItems(queue.status.value, evidence.items.value).size
            _state.update { it.copy(retrying = false, notice = if (left == 0 && r.stall == null) DriverNotice.SYNCED else it.notice) }
        }
    }

    /** Recalcula el viaje mostrado con la cola actual (optimista). */
    private fun recompute() {
        val pending = queue.pending.value
        _state.update { s ->
            val shown = base?.withPending(pending) ?: s.trip
            s.copy(trip = shown, pendingActions = pending.filter { it.shipmentId == shown?.id }, form = s.form.forStop(shown?.driverAction()?.stop?.id))
        }
    }

    private fun StopForm.forStop(stopId: String?) = if (this.stopId == stopId) this else StopForm(stopId)

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
                        form = it.form.forStop(shown?.driverAction()?.stop?.id),
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
        val g = routes.between(trip.originCity, trip.destinationCity)
        if (g != null) _state.update { if (it.trip?.id == trip.id) it.copy(route = g) else it }
    }

    /** "Viajes anteriores" (V15, F22): una llamada barata; si falla, la sección simplemente no aparece. */
    private suspend fun loadHistory() {
        repo.driverHistory().onSuccess { list -> _state.update { it.copy(history = list.take(HISTORY_LIMIT)) } }
    }

    private fun hasSomethingToSend() = queue.pending.value.isNotEmpty() || _state.value.allEvidence.any { it.pendingUpload }

    /**
     * Envía la cola ([now] = olvida la espera exponencial: el conductor o el sistema pidieron reintentar ya). 200 → el envío
     * del servidor pasa a ser la base; error definitivo → se descarta, se recarga del servidor y se explica.
     */
    private suspend fun flushQueue(now: Boolean = false): FlushResult {
        if (!hasSomethingToSend()) return FlushResult()
        val r = if (now) queue.retryNow() else queue.flush()
        handleFlush(r)
        return r
    }

    /**
     * Aplica un resultado de la cola: el último envío del servidor, el aviso sin conexión y la explicación de cada descarte
     * (`TRK-006`/`TRK-009` → falta la foto; `TRK-005` → incidencia; `TRK-010` → bajo el peso; `TRK-011` → bajo el receptor;
     * el resto, el mensaje del servidor).
     */
    private suspend fun handleFlush(r: FlushResult) {
        r.latest?.let { server ->
            if (base == null || base?.id == server.id) {
                base = server
                baseSavedAt = runCatching { cache.save(server) }.getOrNull()
                _state.update { it.copy(offline = false, cachedAt = null) }
            }
        }
        recompute()
        _state.update {
            it.copy(
                queueStall = r.stall,
                offline = if (r.stall == QueueStall.NETWORK) true else it.offline,
                cachedAt = if (r.stall == QueueStall.NETWORK) baseSavedAt else it.cachedAt,
            )
        }
        if (r.rejections.isEmpty()) return
        fetch(initial = false, silent = true)
        r.rejections.forEach { rejection ->
            val e = rejection.error
            val field = (e as? ApiException)?.field
            when (e.apiCode) {
                TrackingErrors.MISSING_PHOTO, TrackingErrors.MISSING_PICKUP_PHOTO -> _state.update { it.copy(photoMissingRejected = true) }
                TrackingErrors.ACTIVE_INCIDENT -> _state.update { it.copy(incidentRejected = true) }
                TrackingErrors.BAD_WEIGHT -> _state.update { it.copy(form = it.form.copy(weightError = UiText.Raw(e.userMessage()))) }
                TrackingErrors.BAD_RECEIVER -> _state.update {
                    if (field == "receivedByDoc") it.copy(form = it.form.copy(receiverDocError = UiText.Raw(e.userMessage())))
                    else it.copy(form = it.form.copy(receiverNameError = UiText.Raw(e.userMessage())))
                }
                else -> _state.update { it.copy(actionError = e.userMessage()) }
            }
        }
    }

    /**
     * El conductor confirmó la acción actual (arrive/complete): se guarda en la cola con la hora del dispositivo (y el peso o
     * quién recibe), se ve aplicada al instante y se envía. Sin señal queda pendiente («Se enviará al recuperar señal»).
     */
    fun advance() {
        val s = _state.value
        val trip = s.trip ?: return
        val action = s.action ?: return
        if (!s.canAdvance) return
        _state.update { it.copy(acting = true, actionError = null, photoMissingRejected = false) }
        viewModelScope.launch {
            val queued = when (action.step) {
                DriverStep.ARRIVE_PICKUP, DriverStep.ARRIVE_DESTINATION -> queue.enqueue(trip.id, action.stop.id, StopActionOp.ARRIVE)
                DriverStep.PICKED_UP -> queue.enqueue(
                    trip.id, action.stop.id, StopActionOp.COMPLETE,
                    needsPhoto = trip.pickupPhotoRequired, photoType = DocumentType.PICKUP_PHOTO,
                    weightKg = s.weightKg?.takeIf { isValidLoadedWeight(it) },
                )
                DriverStep.DELIVERED -> queue.enqueue(
                    trip.id, action.stop.id, StopActionOp.COMPLETE, needsPhoto = true, photoType = DocumentType.DELIVERY_PHOTO,
                    receivedByName = s.form.receiverName.trim(), receivedByDoc = ReceiverDoc.normalize(s.form.receiverDoc),
                )
            }
            recompute()
            val sending = async { flushQueue() }
            val r = withTimeoutOrNull(ACTING_MAX_MS) { sending.await() }
            val stillQueued = queue.pending.value.any { it.id == queued.id }
            val notice = when {
                r?.rejections?.any { it.action.id == queued.id } == true -> null // el error ya se muestra sobre el botón o el campo
                r == null || stillQueued -> DriverNotice.QUEUED
                _state.value.delivered -> DriverNotice.DELIVERED
                else -> DriverNotice.STOP_UPDATED
            }
            _state.update { it.copy(acting = false, notice = notice) }
        }
    }

    // ── Peso del recojo y quién recibe (2.5) ──

    fun onWeight(text: String) = _state.update { it.copy(form = it.form.copy(weight = text.take(9), weightError = null)) }

    fun onReceiverName(text: String) =
        _state.update { it.copy(form = it.form.copy(receiverName = text.take(pe.logyx.core.model.MAX_RECEIVER_NAME), receiverNameError = null)) }

    fun onReceiverDoc(text: String) = _state.update { it.copy(form = it.form.copy(receiverDoc = text.take(20), receiverDocError = null)) }

    // ── Evidencia ──

    /**
     * Foto capturada: [type] null = la de la parada actual (foto de la carga en el recojo, foto de entrega en la entrega).
     * Se comprime, se guarda en la cola y se sube.
     */
    fun onPhotoCaptured(bytes: ByteArray, type: DocumentType? = null) {
        val resolved = type ?: if (_state.value.pickupStep) DocumentType.PICKUP_PHOTO else DocumentType.DELIVERY_PHOTO
        enqueueEvidence(resolved, bytes, "image/jpeg")
    }

    /** Foto de la guía de remisión (recojo, opcional). */
    fun onGuideCaptured(bytes: ByteArray) = enqueueEvidence(DocumentType.REMISSION_GUIDE, bytes, "image/jpeg")

    /** Firma del receptor (PNG). */
    fun onSignatureCaptured(png: ByteArray) = enqueueEvidence(DocumentType.SIGNATURE, png, "image/png")

    private fun enqueueEvidence(type: DocumentType, bytes: ByteArray, mime: String) {
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

    // ── Emergencia (2.7) ──

    /**
     * Botón «Emergencia» confirmado: toma la hora del dispositivo ahora, espera la ubicación como máximo unos segundos
     * ([locate] devuelve null sin permiso o sin fijación) y encola el reporte (con su `Idempotency-Key`). Funciona sin señal.
     */
    fun reportEmergency(kind: EmergencyKind, details: String, locate: suspend () -> DeviceLocation? = { null }) {
        val trip = _state.value.trip ?: return
        if (_state.value.emergencySending) return
        val occurredAt = clock.isoNow()
        _state.update { it.copy(emergencySending = true, actionError = null) }
        viewModelScope.launch {
            val location = runCatching { withTimeoutOrNull(LOCATION_MAX_MS) { locate() } }.getOrNull()
            val request = ReportIncidentRequest(
                category = kind.category,
                description = details.trim().take(MAX_EMERGENCY_TEXT).ifBlank { kind.defaultDescription },
                severity = kind.severity,
                latitude = location?.latitude,
                longitude = location?.longitude,
                occurredAt = occurredAt,
            )
            val queued = queue.enqueueIncident(trip.id, request)
            recompute()
            val sending = async { flushQueue() }
            val r = withTimeoutOrNull(ACTING_MAX_MS) { sending.await() }
            val rejected = r?.rejections?.any { it.action.id == queued.id } == true
            val stillQueued = queue.pending.value.any { it.id == queued.id }
            val notice = when {
                rejected -> null // el mensaje del servidor ya está en actionError
                r == null || stillQueued -> DriverNotice.EMERGENCY_QUEUED
                else -> DriverNotice.EMERGENCY_SENT
            }
            _state.update { it.copy(emergencySending = false, notice = notice) }
            if (!rejected && !stillQueued && r != null) fetch(initial = false, silent = true) // el viaje queda detenido (RN11)
        }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }
}
