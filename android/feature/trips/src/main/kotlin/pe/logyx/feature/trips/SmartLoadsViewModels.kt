package pe.logyx.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.SmartLoadsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.model.ReturnLoadView
import pe.logyx.core.model.SmartLoadStatus
import pe.logyx.core.model.SmartLoadView
import pe.logyx.core.network.ApiException

// ───────────────────────── Cargas de retorno ─────────────────────────

/** Hoja de oferta sobre una carga de retorno; el vehículo es el sugerido por el backend. */
data class ReturnBidState(
    val load: ReturnLoadView,
    val amountText: String,
    val amountError: UiText? = null,
    val confirming: Boolean = false,
    val submitting: Boolean = false,
)

data class ReturnLoadsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<ReturnLoadView> = emptyList(),
    val bid: ReturnBidState? = null,
    val message: UiMessage? = null,
)

class ReturnLoadsViewModel(
    private val smart: SmartLoadsRepository,
    private val loads: LoadsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReturnLoadsUiState())
    val state: StateFlow<ReturnLoadsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            smart.returnLoads().fold(
                onSuccess = { list ->
                    _state.update { it.copy(loading = false, refreshing = false, items = list.sortedByDescending { r -> r.matchScore }) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun openBid(load: ReturnLoadView) {
        if (load.alreadyBid) return
        _state.update { it.copy(bid = ReturnBidState(load, amountText = pe.logyx.core.designsystem.components.moneyToInput(load.suggestedPrice))) }
    }

    fun dismissBid() = _state.update { if (it.bid?.submitting == true) it else it.copy(bid = null) }
    fun onAmount(text: String) = _state.update { s -> s.bid?.let { s.copy(bid = it.copy(amountText = text, amountError = null)) } ?: s }
    fun dismissConfirm() = _state.update { s -> s.bid?.let { if (it.submitting) s else s.copy(bid = it.copy(confirming = false)) } ?: s }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun validate(bid: ReturnBidState): UiText? {
        val amount = parseMoney(bid.amountText)
        val floor = bid.load.priceFloor
        return when {
            amount == null || amount <= 0 -> UiText.of(R.string.trips_bid_err_amount)
            floor != null && amount < floor -> UiText.of(R.string.trips_bid_err_floor, Fmt.money(floor))
            else -> null
        }
    }

    /** Valida el monto y pide confirmación antes de ofertar. */
    fun requestConfirm() {
        val bid = _state.value.bid ?: return
        if (bid.submitting) return
        val error = validate(bid)
        _state.update { it.copy(bid = bid.copy(amountError = error, confirming = error == null)) }
    }

    fun submit() {
        val bid = _state.value.bid ?: return
        if (bid.submitting) return
        val error = validate(bid)
        if (error != null) {
            _state.update { it.copy(bid = bid.copy(amountError = error, confirming = false)) }
            return
        }
        _state.update { it.copy(bid = bid.copy(submitting = true)) }
        viewModelScope.launch {
            loads.placeBid(bid.load.requestId, bid.load.vehicleId, parseMoney(bid.amountText)!!, null).fold(
                onSuccess = {
                    _state.update { it.copy(bid = null, message = UiMessage.success(R.string.trips_bid_sent)) }
                    load(initial = false)
                },
                onFailure = { e ->
                    val onAmount = (e as? ApiException)?.field == "amount"
                    _state.update {
                        if (onAmount) it.copy(bid = bid.copy(submitting = false, confirming = false, amountError = UiText.Raw(e.userMessage())))
                        else it.copy(bid = null, message = UiMessage.error(e.userMessage()))
                    }
                },
            )
        }
    }
}

// ───────────────────────── Sugerencias Smart Load ─────────────────────────

data class SmartAction(val item: SmartLoadView, val accept: Boolean)

data class SmartLoadsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val filter: SmartLoadStatus = SmartLoadStatus.PENDING,
    val items: List<SmartLoadView> = emptyList(),
    val generating: Boolean = false,
    val pending: SmartAction? = null,
    val busy: Boolean = false,
    val message: UiMessage? = null,
)

class SmartLoadsViewModel(private val smart: SmartLoadsRepository) : ViewModel() {
    private val _state = MutableStateFlow(SmartLoadsUiState())
    val state: StateFlow<SmartLoadsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    fun setFilter(status: SmartLoadStatus) {
        if (status == _state.value.filter) return
        _state.update { it.copy(filter = status, items = emptyList()) }
        load(initial = true)
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        val status = _state.value.filter
        viewModelScope.launch {
            smart.smartLoads(status).fold(
                onSuccess = { list ->
                    // Descarta respuestas de un filtro anterior si el usuario ya cambió de pestaña.
                    if (status == _state.value.filter) _state.update { it.copy(loading = false, refreshing = false, items = list) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun generate() {
        if (_state.value.generating) return
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            smart.generate().fold(
                onSuccess = { created ->
                    val msg = when (created.size) {
                        0 -> UiMessage.info(R.string.trips_smart_generated_none)
                        1 -> UiMessage.success(R.string.trips_smart_generated_one)
                        else -> UiMessage.success(R.string.trips_smart_generated_many, created.size)
                    }
                    _state.update { it.copy(generating = false, message = msg, filter = SmartLoadStatus.PENDING) }
                    load(initial = false)
                },
                onFailure = { e -> _state.update { it.copy(generating = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun askAccept(item: SmartLoadView) = ask(SmartAction(item, accept = true))
    fun askReject(item: SmartLoadView) = ask(SmartAction(item, accept = false))
    private fun ask(a: SmartAction) = _state.update { if (it.busy) it else it.copy(pending = a) }
    fun dismissConfirm() = _state.update { if (it.busy) it else it.copy(pending = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun confirm() {
        val action = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = if (action.accept) smart.accept(action.item.id) else smart.reject(action.item.id)
            result.fold(
                onSuccess = {
                    val msg = if (action.accept) R.string.trips_smart_accepted else R.string.trips_smart_rejected
                    _state.update { it.copy(busy = false, pending = null, message = UiMessage.success(msg)) }
                    load(initial = false)
                },
                onFailure = { e ->
                    // Vencida/ya resuelta: el mensaje de la API explica y la lista se actualiza.
                    _state.update { it.copy(busy = false, pending = null, message = UiMessage.error(e.userMessage())) }
                    load(initial = false)
                },
            )
        }
    }
}
