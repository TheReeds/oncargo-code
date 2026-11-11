package pe.logyx.feature.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint
import pe.logyx.core.network.ApiException

// ───────────────────────── Mis solicitudes ─────────────────────────

data class RequestsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<RequestView> = emptyList(),
)

class RequestsViewModel(private val repo: RequestsRepository) : ViewModel() {
    private val _state = MutableStateFlow(RequestsUiState())
    val state: StateFlow<RequestsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.mine().fold(
                onSuccess = { list ->
                    _state.update { it.copy(loading = false, refreshing = false, items = list.sortedByDescending { r -> r.createdAt.orEmpty() }) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

// ───────────────────────── Detalle: comparar y aceptar ─────────────────────────

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

data class RequestDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val request: RequestView? = null,
    val bids: List<BidRow> = emptyList(),
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Oferta pendiente de confirmar en el diálogo "¿Aceptar?". */
    val confirmBid: BidView? = null,
    val confirmCancel: Boolean = false,
    val busy: Boolean = false,
    val counter: CounterDraft? = null,
    val message: UiMessage? = null,
    /** Se llena al aceptar: la UI navega al seguimiento y llama [RequestDetailViewModel.navigationHandled]. */
    val createdShipmentId: String? = null,
) {
    val canDecide: Boolean get() = request?.status == RequestStatus.PUBLISHED
}

internal fun buildBidRows(bids: List<BidView>, suggested: Double?): List<BidRow> {
    val sorted = bids.sortedBy { it.amount }
    val best = sorted.firstOrNull { it.status != BidStatus.REJECTED }
    return sorted.map { b ->
        BidRow(b, isBest = b.id == best?.id, diffVsSuggestedPct = suggested?.takeIf { it > 0 }?.let { (b.amount - it) / it * 100.0 })
    }
}

class RequestDetailViewModel(
    private val requestId: String,
    private val repo: RequestsRepository,
    private val catalog: CatalogRepository,
    private val negotiation: NegotiationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RequestDetailUiState())
    val state: StateFlow<RequestDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.request == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val req = async { repo.detail(requestId) }
            val bids = async { repo.bids(requestId) }
            val cities = async { catalog.cities() }
            req.await().fold(
                onSuccess = { r ->
                    val list = cities.await().getOrDefault(emptyList())
                    fun geo(code: String) = list.firstOrNull { normalizeCity(it.code) == normalizeCity(code) }?.toGeoPoint()
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, request = r,
                            bids = buildBidRows(bids.await().getOrDefault(emptyList()), r.suggestedPrice),
                            origin = geo(r.originCity), destination = geo(r.destinationCity),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun askAccept(bid: BidView) = _state.update { it.copy(confirmBid = bid) }
    fun dismissDialogs() = _state.update { it.copy(confirmBid = null, confirmCancel = false) }
    fun askCancel() = _state.update { it.copy(confirmCancel = true) }
    fun onMessageShown() = _state.update { it.copy(message = null) }
    fun navigationHandled() = _state.update { it.copy(createdShipmentId = null) }

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

    fun confirmCancel() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.cancel(requestId).fold(
                onSuccess = { _state.update { it.copy(busy = false, confirmCancel = false, message = UiMessage.success(R.string.requests_cancelled)) }; load(initial = false) },
                onFailure = { e -> _state.update { it.copy(busy = false, confirmCancel = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    // ── Contraoferta (RF27, RN03) ──

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
    fun onCounterMessage(text: String) = updateCounter { it.copy(message = text, messageError = null) }
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
                    val api = e as? ApiException
                    when (api?.field) {
                        "amount" -> updateCounter { it.copy(sending = false, confirming = false, amountError = UiText.Raw(e.userMessage())) }
                        "message" -> updateCounter { it.copy(sending = false, confirming = false, messageError = UiText.Raw(e.userMessage())) }
                        else -> {
                            updateCounter { it.copy(sending = false, confirming = false) }
                            _state.update { it.copy(message = UiMessage.error(e.userMessage())) }
                        }
                    }
                },
            )
        }
    }
}
