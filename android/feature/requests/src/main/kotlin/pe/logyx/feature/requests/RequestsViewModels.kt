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
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.BidView
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint

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
    val message: String? = null,
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
    fun messageShown() = _state.update { it.copy(message = null) }
    fun navigationHandled() = _state.update { it.copy(createdShipmentId = null) }

    fun confirmAccept() {
        val bid = _state.value.confirmBid ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, confirmBid = null) }
        viewModelScope.launch {
            repo.acceptBid(bid.id).fold(
                onSuccess = { res ->
                    _state.update { it.copy(busy = false, createdShipmentId = res.shipmentId, message = "Oferta aceptada. Se creó tu envío.") }
                    load(initial = false)
                },
                onFailure = { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } },
            )
        }
    }

    fun confirmCancel() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, confirmCancel = false) }
        viewModelScope.launch {
            repo.cancel(requestId).fold(
                onSuccess = { _state.update { it.copy(busy = false, message = "Solicitud cancelada.") }; load(initial = false) },
                onFailure = { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } },
            )
        }
    }
}
