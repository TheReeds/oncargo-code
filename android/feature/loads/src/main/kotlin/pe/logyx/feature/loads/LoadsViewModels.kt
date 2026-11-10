package pe.logyx.feature.loads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.VehicleView
import pe.logyx.core.model.canCarry
import pe.logyx.core.model.isActive
import pe.logyx.core.model.isCompatibleWith
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint

private fun AuthRepository.currentUser() = (state.value as? SessionState.LoggedIn)?.user

// ───────────────────────── Home "Tu actividad" ─────────────────────────

data class CarrierHomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val userName: String = "",
    val suggested: List<RequestView> = emptyList(),
    val compatibleCount: Int = 0,
    val pendingBids: Int = 0,
    val totalBids: Int = 0,
    val activeShipment: ShipmentView? = null,
    val vehicles: List<VehicleView> = emptyList(),
)

class CarrierHomeViewModel(
    private val auth: AuthRepository,
    private val loads: LoadsRepository,
    private val shipments: ShipmentsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CarrierHomeUiState(userName = auth.currentUser()?.fullName.orEmpty()))
    val state: StateFlow<CarrierHomeUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.suggested.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            val market = async { loads.marketplace() }
            val fleet = async { loads.vehicles() }
            val bids = async { loads.myBids() }
            val ships = async { shipments.list() }
            val marketRes = market.await()
            val vehicles = fleet.await().getOrDefault(emptyList())
            val myBids = bids.await().getOrDefault(emptyList())
            val active = ships.await().getOrDefault(emptyList()).firstOrNull { it.isActive }
            marketRes.fold(
                onSuccess = { all ->
                    val compatible = all.filter { it.myBid == null && it.isCompatibleWith(vehicles) }
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, error = null,
                            suggested = compatible.sortedByDescending { r -> r.suggestedPrice ?: 0.0 }.take(5),
                            compatibleCount = compatible.size,
                            pendingBids = myBids.count { r -> r.myBid?.status == BidStatus.PENDING },
                            totalBids = myBids.size,
                            activeShipment = active,
                            vehicles = vehicles,
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

// ───────────────────────── Feed de cargas ─────────────────────────

enum class LoadFilter { MyVehicle, BestPay, Nearby }

data class LoadsFeedUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val all: List<RequestView> = emptyList(),
    val vehicles: List<VehicleView> = emptyList(),
    val userCity: String? = null,
    val filters: Set<LoadFilter> = emptySet(),
) {
    fun isCompatible(r: RequestView) = r.isCompatibleWith(vehicles)

    val visible: List<RequestView>
        get() {
            var list = all
            if (LoadFilter.MyVehicle in filters) list = list.filter { isCompatible(it) }
            if (LoadFilter.Nearby in filters && userCity != null) {
                val city = normalizeCity(userCity)
                list = list.filter { normalizeCity(it.originCity) == city }
            }
            return if (LoadFilter.BestPay in filters) list.sortedByDescending { it.suggestedPrice ?: 0.0 }
            else list.sortedByDescending { it.createdAt.orEmpty() }
        }

    /** "Cerca de mí" requiere la ciudad de la organización. */
    val nearbyUnavailable: Boolean get() = LoadFilter.Nearby in filters && userCity == null
}

class LoadsFeedViewModel(
    private val auth: AuthRepository,
    private val loads: LoadsRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoadsFeedUiState())
    val state: StateFlow<LoadsFeedUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    fun toggle(filter: LoadFilter) = _state.update {
        it.copy(filters = if (filter in it.filters) it.filters - filter else it.filters + filter)
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.all.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            val market = async { loads.marketplace() }
            val fleet = async { loads.vehicles() }
            val city = async { auth.currentUser()?.let { u -> catalog.organization(u.organizationId).getOrNull()?.city } }
            market.await().fold(
                onSuccess = { all ->
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, all = all,
                            vehicles = fleet.await().getOrDefault(emptyList()), userCity = city.await(),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

// ───────────────────────── Detalle + ofertar ─────────────────────────

/** Estado del bottom sheet para ofertar. */
data class BidFormState(
    val vehicleId: String? = null,
    val amountText: String = "",
    val notes: String = "",
    val submitting: Boolean = false,
    /** Error de validación local o mensaje de la API (se muestra tal cual). */
    val error: String? = null,
)

data class LoadDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val request: RequestView? = null,
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    val vehicles: List<VehicleView> = emptyList(),
    val form: BidFormState? = null,
    /** Mensaje efímero (snackbar); la UI lo consume con [LoadDetailViewModel.messageShown]. */
    val message: String? = null,
) {
    val compatibleVehicles: List<VehicleView>
        get() = request?.let { r -> vehicles.filter { it.canCarry(r) } } ?: emptyList()
}

class LoadDetailViewModel(
    private val requestId: String,
    private val loads: LoadsRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoadDetailUiState())
    val state: StateFlow<LoadDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.request == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val req = async { loads.load(requestId) }
            val cities = async { catalog.cities() }
            val fleet = async { loads.vehicles() }
            req.await().fold(
                onSuccess = { r ->
                    val list = cities.await().getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, request = r,
                            origin = list.find(r.originCity)?.toGeoPoint(), destination = list.find(r.destinationCity)?.toGeoPoint(),
                            vehicles = fleet.await().getOrDefault(emptyList()),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    private fun List<CityItem>.find(code: String) = firstOrNull { normalizeCity(it.code) == normalizeCity(code) }

    fun openBidSheet() {
        val s = _state.value
        val only = s.compatibleVehicles.singleOrNull()
        _state.update { it.copy(form = BidFormState(vehicleId = only?.id)) }
    }

    fun dismissBidSheet() = _state.update { if (it.form?.submitting == true) it else it.copy(form = null) }

    fun selectVehicle(id: String) = updateForm { it.copy(vehicleId = id, error = null) }
    fun onAmountChange(text: String) = updateForm { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null) }
    fun onNotesChange(text: String) = updateForm { it.copy(notes = text.take(300)) }

    private fun updateForm(f: (BidFormState) -> BidFormState) = _state.update { s -> s.form?.let { s.copy(form = f(it)) } ?: s }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun submitBid() {
        val s = _state.value
        val form = s.form ?: return
        val request = s.request ?: return
        if (form.submitting) return
        val amount = parseAmount(form.amountText)
        val floor = request.priceFloor
        val error = when {
            form.vehicleId == null -> "Elige el vehículo con el que harás el viaje."
            amount == null || amount <= 0 -> "Ingresa un monto válido."
            floor != null && amount < floor -> "El monto no puede ser menor al piso de ${pe.logyx.core.designsystem.Fmt.money(floor)}."
            else -> null
        }
        if (error != null) {
            updateForm { it.copy(error = error) }
            return
        }
        updateForm { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            loads.placeBid(request.id, form.vehicleId!!, amount!!, form.notes).fold(
                onSuccess = {
                    _state.update { st -> st.copy(form = null, message = "¡Oferta enviada!") }
                    load(initial = false)
                },
                // Se muestra el mensaje de la API (p. ej. monto bajo el piso, subasta cerrada, ya ofertaste).
                onFailure = { e -> updateForm { it.copy(submitting = false, error = e.userMessage()) } },
            )
        }
    }
}

/** Acepta `1850`, `1850.50`, `1850,50` y `1,850.50`. */
internal fun parseAmount(text: String): Double? {
    val t = text.trim()
    val normalized = if (t.contains('.') && t.contains(',')) t.replace(",", "") else t.replace(',', '.')
    return normalized.toDoubleOrNull()
}

// ───────────────────────── Mis ofertas ─────────────────────────

data class MyBidsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<RequestView> = emptyList(),
)

class MyBidsViewModel(private val loads: LoadsRepository) : ViewModel() {
    private val _state = MutableStateFlow(MyBidsUiState())
    val state: StateFlow<MyBidsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            loads.myBids().fold(
                onSuccess = { list ->
                    _state.update { it.copy(loading = false, refreshing = false, items = list.sortedByDescending { r -> r.myBid?.createdAt.orEmpty() }) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}
