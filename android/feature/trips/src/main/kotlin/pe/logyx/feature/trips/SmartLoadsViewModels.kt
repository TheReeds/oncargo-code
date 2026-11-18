package pe.logyx.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.SmartLoadsRepository
import pe.logyx.core.data.fieldErrors
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.userMessage
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.moneyToInput
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.model.AcceptSmartLoadRequest
import pe.logyx.core.model.ReturnLoadView
import pe.logyx.core.model.SmartLoadItemAmount
import pe.logyx.core.model.SmartLoadItemView
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

/** Piso orientativo de RN03 (75 % del precio sugerido): solo una pista en el móvil, el servidor manda. */
internal const val SMART_FLOOR_RATIO = 0.75

/** Máximo de las notas del plan (`@Size(max = 300)`, RN07 en `notes`). */
internal const val MAX_SMART_NOTES = 300

/** Piso orientativo de una carga del plan; el precio sugerido llega como `income`. */
internal fun SmartLoadItemView.floorHint(): Double = Math.round(income * SMART_FLOOR_RATIO * 100.0) / 100.0

/**
 * Hoja "Ofertar este plan" (F12): un monto por carga (todo o nada, `TRP-011`), notas opcionales y confirmación.
 * [amounts] guarda el texto crudo por `requestId`; se precarga con el precio sugerido de cada carga (`income`).
 */
data class SmartAcceptState(
    val item: SmartLoadView,
    val amounts: Map<String, String>,
    val amountErrors: Map<String, UiText> = emptyMap(),
    val notes: String = "",
    val notesError: UiText? = null,
    /** Error del plan completo (`TRP-011`, `TRP-012`, plan vencido…): se muestra como aviso en la hoja. */
    val error: UiText? = null,
    val confirming: Boolean = false,
    val submitting: Boolean = false,
) {
    val total: Double get() = amounts.values.sumOf { parseMoney(it) ?: 0.0 }
}

data class SmartLoadsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val filter: SmartLoadStatus = SmartLoadStatus.PENDING,
    val items: List<SmartLoadView> = emptyList(),
    val generating: Boolean = false,
    /** Confirmación de rechazar. */
    val pending: SmartAction? = null,
    /** Hoja de montos para aceptar (ofertar en lote). */
    val accept: SmartAcceptState? = null,
    /** Plan aceptado con las ofertas creadas (`bids`): tarjeta de resultado. */
    val accepted: SmartLoadView? = null,
    val busy: Boolean = false,
    val message: UiMessage? = null,
)

class SmartLoadsViewModel(private val smart: SmartLoadsRepository, private val realtime: RealtimeRepository) : ViewModel() {
    private val _state = MutableStateFlow(SmartLoadsUiState())
    val state: StateFlow<SmartLoadsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    fun setFilter(status: SmartLoadStatus) {
        if (status == _state.value.filter) return
        _state.update { it.copy(filter = status, items = emptyList()) }
        load(initial = true)
    }

    /** Tiempo real (solo en primer plano, lo lanza la UI): `smart_load.*` refresca en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(setOf(RealtimeTypes.SMART_LOAD_SUGGESTED, RealtimeTypes.SMART_LOAD_ACCEPTED)).collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        val status = _state.value.filter
        viewModelScope.launch {
            smart.smartLoads(status).fold(
                onSuccess = { list ->
                    // Descarta respuestas de un filtro anterior si el usuario ya cambió de pestaña.
                    if (status == _state.value.filter) _state.update { it.copy(loading = false, refreshing = false, items = list) }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
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

    /** Abre la hoja de montos por carga (precargados con el precio sugerido). */
    fun askAccept(item: SmartLoadView) = _state.update {
        if (it.busy) it
        else it.copy(accept = SmartAcceptState(item, item.items.associate { i -> i.requestId to moneyToInput(i.income.takeIf { v -> v > 0 }) }))
    }

    fun askReject(item: SmartLoadView) = _state.update { if (it.busy) it else it.copy(pending = SmartAction(item, accept = false)) }
    fun dismissConfirm() = _state.update { if (it.busy) it else it.copy(pending = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }
    fun dismissAccepted() = _state.update { it.copy(accepted = null) }

    private fun updateAccept(f: (SmartAcceptState) -> SmartAcceptState) = _state.update { s -> s.accept?.let { s.copy(accept = f(it)) } ?: s }

    fun dismissAccept() = _state.update { if (it.accept?.submitting == true) it else it.copy(accept = null) }
    fun onItemAmount(requestId: String, text: String) =
        updateAccept { it.copy(amounts = it.amounts + (requestId to text), amountErrors = it.amountErrors - requestId, error = null) }
    fun onNotes(text: String) = updateAccept { it.copy(notes = text.take(MAX_SMART_NOTES), notesError = null) }
    fun cancelAcceptConfirm() = updateAccept { if (it.submitting) it else it.copy(confirming = false) }

    /** Valida que cada carga tenga un monto positivo y pide confirmación. */
    fun requestAcceptConfirm() {
        val form = _state.value.accept ?: return
        if (form.submitting) return
        val errors = form.item.items.mapNotNull { i ->
            val amount = parseMoney(form.amounts[i.requestId].orEmpty())
            if (amount == null || amount <= 0.0) i.requestId to UiText.of(R.string.trips_smart_amount_required) else null
        }.toMap()
        updateAccept { if (errors.isEmpty()) it.copy(confirming = true, amountErrors = emptyMap(), error = null) else it.copy(amountErrors = errors, confirming = false) }
    }

    /** `POST /smart-loads/{id}/accept`: una oferta por carga, todo o nada (`TRP-011`). */
    fun confirmAccept() {
        val form = _state.value.accept ?: return
        if (form.submitting) return
        val items = form.item.items.map { SmartLoadItemAmount(it.requestId, parseMoney(form.amounts[it.requestId].orEmpty()) ?: return) }
        updateAccept { it.copy(submitting = true, error = null, notesError = null) }
        viewModelScope.launch {
            smart.accept(form.item.id, AcceptSmartLoadRequest(items, form.notes.trim().takeIf { it.isNotEmpty() })).fold(
                onSuccess = { plan ->
                    _state.update { it.copy(accept = null, accepted = plan, message = UiMessage.success(R.string.trips_smart_accepted)) }
                    load(initial = false)
                },
                onFailure = { e ->
                    val notes = e.fieldErrors("notes")["notes"]
                    updateAccept {
                        if (notes != null) it.copy(submitting = false, confirming = false, notesError = UiText.Raw(notes))
                        // TRP-011 (todo o nada), TRP-012 (carga fuera del plan), plan vencido o sin acceso: aviso en la hoja.
                        else it.copy(submitting = false, confirming = false, error = UiText.Raw(e.userMessage()))
                    }
                },
            )
        }
    }

    fun confirm() {
        val action = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            smart.reject(action.item.id).fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, pending = null, message = UiMessage.success(R.string.trips_smart_rejected)) }
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
