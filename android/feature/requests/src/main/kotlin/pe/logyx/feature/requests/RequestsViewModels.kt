package pe.logyx.feature.requests

import pe.logyx.core.data.RoutesRepository
import pe.logyx.core.data.between
import pe.logyx.core.model.RouteGeometry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.data.fieldErrors
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CounterOfferStatus
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.isPendingFor
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint
import pe.logyx.core.designsystem.components.MAX_REASON_LENGTH
import java.time.LocalDate

// ───────────────────────── Mis solicitudes ─────────────────────────

data class RequestsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<RequestView> = emptyList(),
    /** Reservas de espacio en viajes que siguen vivas (PENDING o CONFIRMED); null si el API no lo envió. */
    val activeTripBookings: Int? = null,
)

class RequestsViewModel(
    private val repo: RequestsRepository,
    private val realtime: RealtimeRepository,
    private val loads: LoadsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RequestsUiState())
    val state: StateFlow<RequestsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano): ofertas/estado de mis solicitudes refrescan la lista en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(REQUEST_LIST_TYPES).collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            val dash = async { loads.dashboard().getOrNull() }
            val mine = repo.mine()
            val tripBookings = dash.await()?.activeTripBookings
            mine.fold(
                onSuccess = { list ->
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, items = list.sortedByDescending { r -> r.createdAt.orEmpty() },
                            activeTripBookings = tripBookings,
                        )
                    }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

/** Eventos que cambian lo que la PYME ve de sus solicitudes (`bid.placed`, `request.matched`, contraofertas, `auction.updated`). */
private val REQUEST_TYPES = setOf(
    RealtimeTypes.BID_PLACED, RealtimeTypes.COUNTER_OFFER_UPDATED, RealtimeTypes.REQUEST_MATCHED, RealtimeTypes.AUCTION_UPDATED,
)
private val REQUEST_LIST_TYPES = REQUEST_TYPES + RealtimeTypes.TRIP_BOOKING_UPDATED

// ───────────────────────── Detalle: comparar y aceptar ─────────────────────────

/** Largo máximo del mensaje de una contraoferta (`@Size(max = 500)` del backend). */
const val MAX_COUNTER_MESSAGE = 500

/** Oferta lista para comparar. [diffVsSuggestedPct] negativo = más barata que el precio sugerido. */
data class BidRow(val bid: BidView, val isBest: Boolean, val diffVsSuggestedPct: Double?)

/** Hoja "Contraofertar" (RF27): borrador, errores de campo y contraofertas previas de la oferta. */
data class CounterDraft(
    val bid: BidView,
    val amount: String = "",
    val message: String = "",
    val amountError: UiText? = null,
    val messageError: UiText? = null,
    val previous: List<CounterOfferView> = emptyList(),
    val loadingPrevious: Boolean = true,
    val confirming: Boolean = false,
    val sending: Boolean = false,
)

/** Respuesta de la PYME a la contraoferta del transportista (RF28): pendiente de confirmar en el diálogo. */
data class CounterResponse(val bid: BidView, val counter: CounterOfferView, val accept: Boolean)

/** Hoja "Cancelar solicitud" con motivo opcional (RN07 en `reason`, máx. 500). */
data class CancelDraft(val reason: String = "", val error: UiText? = null, val sending: Boolean = false)

enum class RescheduleMode { REOPEN, REPUBLISH }

/** Hoja de "Reabrir subasta" o "Republicar": nueva fecha de recojo opcional (`REQ-003` bajo el campo). */
data class RescheduleDraft(
    val mode: RescheduleMode,
    val date: LocalDate? = null,
    val error: UiText? = null,
    val sending: Boolean = false,
)

data class RequestDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val request: RequestView? = null,
    val bids: List<BidRow> = emptyList(),
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Ruta por carretera para el mapa (ADR-0008); null = cargando o sin red (el mapa dibuja la recta). */
    val route: RouteGeometry? = null,
    /** Oferta pendiente de confirmar en el diálogo "¿Aceptar?". */
    val confirmBid: BidView? = null,
    val cancel: CancelDraft? = null,
    val reschedule: RescheduleDraft? = null,
    val busy: Boolean = false,
    val counter: CounterDraft? = null,
    /** Contraoferta PENDING vigente por id de oferta (la del transportista o la propia de la PYME). */
    val counters: Map<String, CounterOfferView> = emptyMap(),
    val respond: CounterResponse? = null,
    val message: UiMessage? = null,
    /** Se llena al aceptar: la UI navega al seguimiento y llama [RequestDetailViewModel.navigationHandled]. */
    val createdShipmentId: String? = null,
    /** Se llena al republicar: la UI abre la solicitud nueva y llama [RequestDetailViewModel.navigationHandled]. */
    val openRequestId: String? = null,
) {
    val canDecide: Boolean get() = request?.status == RequestStatus.PUBLISHED

    /** Contraoferta del transportista que la PYME puede responder (quien la propuso no ve los botones). */
    fun counterToRespond(bidId: String): CounterOfferView? = counters[bidId]?.takeIf { it.isPendingFor(viewerIsCarrier = false) }

    /** Contraoferta propia de la PYME a la espera del transportista. */
    fun counterWaiting(bidId: String): CounterOfferView? =
        counters[bidId]?.takeIf { it.status == CounterOfferStatus.PENDING && !it.proposedByCarrier }
}

internal fun buildBidRows(bids: List<BidView>, suggested: Double?): List<BidRow> {
    val sorted = bids.sortedBy { it.amount }
    // Las retiradas y no elegidas no cuentan como "mejor precio" (el servidor tampoco las cuenta, V15).
    val best = sorted.firstOrNull { it.status == BidStatus.PENDING || it.status == BidStatus.ACCEPTED }
    return sorted.map { b ->
        BidRow(b, isBest = b.id == best?.id, diffVsSuggestedPct = suggested?.takeIf { it > 0 }?.let { (b.amount - it) / it * 100.0 })
    }
}

class RequestDetailViewModel(
    private val requestId: String,
    private val repo: RequestsRepository,
    private val catalog: CatalogRepository,
    private val negotiation: NegotiationRepository,
    private val realtime: RealtimeRepository,
    private val routes: RoutesRepository = RoutesRepository.NONE,
) : ViewModel() {
    private val _state = MutableStateFlow(RequestDetailUiState())
    val state: StateFlow<RequestDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /**
     * Tiempo real (solo en primer plano): `bid.placed`, `counter_offer.updated`, `request.matched` y `auction.updated`
     * de ESTA solicitud refrescan la comparación en silencio; respaldo solo si el stream no está en vivo.
     */
    suspend fun liveLoop() {
        realtime.refreshSignals(REQUEST_TYPES) { it.string("requestId") == requestId }
            .collect { if (!_state.value.busy) load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.request == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val req = async { repo.detail(requestId) }
            val bids = async { repo.bids(requestId) }
            val cities = async { catalog.cities() }
            req.await().fold(
                onSuccess = { r ->
                    val list = cities.await().getOrDefault(emptyList())
                    fun geo(code: String) = list.firstOrNull { normalizeCity(it.code) == normalizeCity(code) }?.toGeoPoint()
                    val offers = bids.await().getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, request = r,
                            bids = buildBidRows(offers, r.suggestedPrice),
                            origin = geo(r.originCity), destination = geo(r.destinationCity),
                        )
                    }
                    routes.between(r.originCity, r.destinationCity)?.let { g -> _state.update { it.copy(route = g) } }
                    if (r.status == RequestStatus.PUBLISHED) loadCounters(offers) else _state.update { it.copy(counters = emptyMap()) }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    /** Solo consulta las ofertas PENDING (las demás ya no admiten contraoferta), con concurrencia limitada. */
    private suspend fun loadCounters(offers: List<BidView>) {
        val gate = Semaphore(4)
        val found = coroutineScope {
            offers.filter { it.status == BidStatus.PENDING }.map { bid ->
                async {
                    gate.withPermit {
                        negotiation.counterOffers(bid.id).getOrNull()
                            ?.filter { it.status == CounterOfferStatus.PENDING }
                            ?.maxByOrNull { it.createdAt.orEmpty() }
                            ?.let { bid.id to it }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        _state.update { it.copy(counters = found) }
    }

    fun askAccept(bid: BidView) = _state.update { it.copy(confirmBid = bid) }
    fun dismissDialogs() = _state.update { if (it.busy) it else it.copy(confirmBid = null, respond = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }
    fun navigationHandled() = _state.update { it.copy(createdShipmentId = null, openRequestId = null) }

    fun confirmAccept() {
        val bid = _state.value.confirmBid ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.acceptBid(bid.id).fold(
                onSuccess = { res ->
                    _state.update { it.copy(busy = false, confirmBid = null, createdShipmentId = res.shipmentId, message = UiMessage.success(R.string.requests_bid_accepted)) }
                    load(initial = false)
                },
                onFailure = { e -> _state.update { it.copy(busy = false, confirmBid = null, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    // ── Contraoferta del transportista (RF26/RF28): la PYME acepta o rechaza ──

    fun askRespond(bid: BidView, accept: Boolean) {
        val counter = _state.value.counterToRespond(bid.id) ?: return
        _state.update { it.copy(respond = CounterResponse(bid, counter, accept)) }
    }

    fun confirmRespond() {
        val r = _state.value.respond ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = if (r.accept) negotiation.accept(r.counter.id) else negotiation.reject(r.counter.id)
            result.fold(
                onSuccess = {
                    val msg = if (r.accept) UiMessage.success(R.string.requests_counter_response_accepted, Fmt.money(r.counter.amount))
                    else UiMessage.success(R.string.requests_counter_response_rejected)
                    _state.update { it.copy(busy = false, respond = null, message = msg) }
                    load(initial = false)
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, respond = null, message = UiMessage.error(e.userMessage())) }
                    load(initial = false)
                },
            )
        }
    }

    // ── Cancelar con motivo opcional (F16, RN07) ──

    fun askCancel() = _state.update { it.copy(cancel = CancelDraft()) }
    fun closeCancel() = _state.update { if (it.cancel?.sending == true) it else it.copy(cancel = null) }
    fun onCancelReason(text: String) = _state.update { s ->
        s.cancel?.let { s.copy(cancel = it.copy(reason = text.take(MAX_REASON_LENGTH), error = null)) } ?: s
    }

    fun confirmCancel() {
        val draft = _state.value.cancel ?: return
        if (draft.sending) return
        _state.update { it.copy(cancel = draft.copy(sending = true, error = null)) }
        viewModelScope.launch {
            repo.cancel(requestId, draft.reason.trim().takeIf { it.isNotEmpty() }).fold(
                onSuccess = {
                    _state.update { it.copy(cancel = null, message = UiMessage.success(R.string.requests_cancelled)) }
                    load(initial = false)
                },
                onFailure = { e ->
                    val fe = e.fieldErrors("reason")
                    if (fe.isEmpty) _state.update { it.copy(cancel = null, message = UiMessage.error(e.userMessage())) }
                    else _state.update { s -> s.copy(cancel = s.cancel?.copy(sending = false, error = UiText.Raw(fe["reason"].orEmpty()))) }
                },
            )
        }
    }

    // ── Reabrir subasta (F07) y republicar (F06/F07) ──

    fun askReopen() = _state.update { it.copy(reschedule = RescheduleDraft(RescheduleMode.REOPEN)) }
    fun askRepublish() = _state.update { it.copy(reschedule = RescheduleDraft(RescheduleMode.REPUBLISH)) }
    fun closeReschedule() = _state.update { if (it.reschedule?.sending == true) it else it.copy(reschedule = null) }
    fun onRescheduleDate(date: LocalDate) = _state.update { s -> s.reschedule?.let { s.copy(reschedule = it.copy(date = date, error = null)) } ?: s }

    fun confirmReschedule() {
        val draft = _state.value.reschedule ?: return
        if (draft.sending) return
        _state.update { it.copy(reschedule = draft.copy(sending = true, error = null)) }
        val pickup = draft.date?.toString()
        viewModelScope.launch {
            val result = when (draft.mode) {
                RescheduleMode.REOPEN -> repo.reopen(requestId, pickup)
                RescheduleMode.REPUBLISH -> repo.republish(requestId, pickup)
            }
            result.fold(
                onSuccess = { r ->
                    when (draft.mode) {
                        RescheduleMode.REOPEN -> {
                            _state.update { it.copy(reschedule = null, message = UiMessage.success(R.string.requests_reopened)) }
                            load(initial = false)
                        }
                        RescheduleMode.REPUBLISH ->
                            _state.update { it.copy(reschedule = null, openRequestId = r.id, message = UiMessage.success(R.string.requests_republished)) }
                    }
                },
                onFailure = { e ->
                    val fe = e.fieldErrors("pickupDate")
                    if (fe.isEmpty) {
                        _state.update { it.copy(reschedule = null, message = UiMessage.error(e.userMessage())) }
                        load(initial = false, silent = true)
                    } else _state.update { s -> s.copy(reschedule = s.reschedule?.copy(sending = false, error = UiText.Raw(fe["pickupDate"].orEmpty()))) }
                },
            )
        }
    }

    // ── Contraoferta de la PYME (RF27, RN03, RN07) ──

    fun openCounter(bid: BidView) {
        _state.update { it.copy(counter = CounterDraft(bid)) }
        viewModelScope.launch {
            val prev = negotiation.counterOffers(bid.id).getOrDefault(emptyList())
            _state.update { s ->
                val c = s.counter
                if (c?.bid?.id == bid.id) s.copy(counter = c.copy(previous = prev.sortedByDescending { o -> o.createdAt.orEmpty() }, loadingPrevious = false)) else s
            }
        }
    }

    fun closeCounter() = _state.update { if (it.counter?.sending == true) it else it.copy(counter = null) }
    fun onCounterAmount(text: String) = updateCounter { it.copy(amount = text, amountError = null) }
    fun onCounterMessage(text: String) = updateCounter { it.copy(message = text.take(MAX_COUNTER_MESSAGE), messageError = null) }
    fun cancelCounterConfirm() = updateCounter { it.copy(confirming = false) }

    private fun updateCounter(f: (CounterDraft) -> CounterDraft) = _state.update { s -> s.counter?.let { s.copy(counter = f(it)) } ?: s }

    /** Valida (>= piso RN03 y menor a la oferta actual) y pide confirmación. */
    fun askConfirmCounter() {
        val s = _state.value
        val c = s.counter ?: return
        val amount = parseMoney(c.amount)
        val floor = s.request?.priceFloor
        val error = when {
            amount == null || amount <= 0.0 -> UiText.of(R.string.requests_counter_amount_required)
            floor != null && amount < floor -> UiText.of(R.string.requests_counter_below_floor, Fmt.money(floor))
            amount >= c.bid.amount -> UiText.of(R.string.requests_counter_not_lower, Fmt.money(c.bid.amount))
            else -> null
        }
        if (error != null) updateCounter { it.copy(amountError = error) } else updateCounter { it.copy(confirming = true) }
    }

    fun confirmCounter() {
        val c = _state.value.counter ?: return
        val amount = parseMoney(c.amount) ?: return
        if (c.sending) return
        updateCounter { it.copy(sending = true) }
        viewModelScope.launch {
            negotiation.propose(c.bid.id, amount, c.message.trim().takeIf { it.isNotEmpty() }).fold(
                onSuccess = {
                    _state.update { it.copy(counter = null, message = UiMessage.success(R.string.requests_counter_sent)) }
                    load(initial = false)
                },
                onFailure = { e ->
                    val fe = e.fieldErrors("amount", "message")
                    if (fe.isEmpty) {
                        updateCounter { it.copy(sending = false, confirming = false) }
                        _state.update { it.copy(message = UiMessage.error(e.userMessage())) }
                    } else {
                        updateCounter {
                            it.copy(
                                sending = false, confirming = false,
                                amountError = fe["amount"]?.let { m -> UiText.Raw(m) } ?: it.amountError,
                                messageError = fe["message"]?.let { m -> UiText.Raw(m) } ?: it.messageError,
                            )
                        }
                    }
                },
            )
        }
    }
}
