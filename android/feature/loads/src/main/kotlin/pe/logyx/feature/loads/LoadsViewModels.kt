package pe.logyx.feature.loads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.DashboardSummary
import pe.logyx.core.model.MarketplaceFilter
import pe.logyx.core.model.MarketplaceSort
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
    val activeShipments: Int? = null,
    val deliveredThisMonth: Int? = null,
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
            val dash = async { loads.dashboard().getOrNull() }
            val marketRes = market.await()
            val vehicles = fleet.await().getOrDefault(emptyList())
            val myBids = bids.await().getOrDefault(emptyList())
            val active = ships.await().getOrDefault(emptyList()).firstOrNull { it.isActive }
            val summary: DashboardSummary? = dash.await()
            marketRes.fold(
                onSuccess = { all ->
                    val compatible = all.filter { it.myBid == null && it.isCompatibleWith(vehicles) }
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, error = null,
                            suggested = compatible.sortedByDescending { r -> r.suggestedPrice ?: 0.0 }.take(5),
                            compatibleCount = summary?.compatibleLoads ?: compatible.size,
                            pendingBids = summary?.pendingBids ?: myBids.count { r -> r.myBid?.status == BidStatus.PENDING },
                            activeShipments = summary?.activeShipments,
                            deliveredThisMonth = summary?.deliveredThisMonth,
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

private const val FEED_PAGE_SIZE = 20

data class LoadsFeedUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val items: List<RequestView> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val vehicles: List<VehicleView> = emptyList(),
    val cities: List<CityItem> = emptyList(),
    val userCity: String? = null,
    val filters: Set<LoadFilter> = emptySet(),
    val originCity: CityItem? = null,
    val destinationCity: CityItem? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val cargoType: CargoType? = null,
    val message: UiMessage? = null,
) {
    fun isCompatible(r: RequestView) = r.isCompatibleWith(vehicles)

    /** El servidor ya filtra y ordena; la lista visible es la cargada. */
    val visible: List<RequestView> get() = items

    /** "Cerca de mí" requiere la ciudad de la organización. */
    val nearbyUnavailable: Boolean get() = LoadFilter.Nearby in filters && userCity == null

    /** Filtros del panel "Más filtros" activos (origen, destino, fechas, tipo). */
    val advancedCount: Int
        get() = listOf(originCity != null, destinationCity != null, dateFrom != null, cargoType != null).count { it }

    val hasActiveFilters: Boolean get() = filters.isNotEmpty() || advancedCount > 0

    fun toServerFilter(): MarketplaceFilter {
        val nearby = if (LoadFilter.Nearby in filters && userCity != null) normalizeCity(userCity) else null
        return MarketplaceFilter(
            originCity = originCity?.code ?: nearby,
            destinationCity = destinationCity?.code,
            pickupFrom = dateFrom?.toString(),
            pickupTo = dateTo?.toString(),
            maxWeightKg = if (LoadFilter.MyVehicle in filters) vehicles.maxOfOrNull { it.maxWeightKg } else null,
            cargoType = cargoType,
            sort = if (LoadFilter.BestPay in filters) MarketplaceSort.PRICE_DESC else MarketplaceSort.PICKUP,
        )
    }
}

class LoadsFeedViewModel(
    private val auth: AuthRepository,
    private val loads: LoadsRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoadsFeedUiState(userCity = auth.currentUser()?.organizationCity))
    val state: StateFlow<LoadsFeedUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val fleet = async { loads.vehicles() }
            val cities = async { catalog.cities() }
            _state.update { it.copy(vehicles = fleet.await().getOrDefault(emptyList()), cities = cities.await().getOrDefault(emptyList())) }
            reload(refreshing = false)
        }
    }

    fun refresh() = reload(refreshing = true)

    fun toggle(filter: LoadFilter) {
        _state.update {
            it.copy(
                filters = if (filter in it.filters) it.filters - filter else it.filters + filter,
                // El chip de cercanía y el origen elegido se excluyen entre sí.
                originCity = if (filter == LoadFilter.Nearby && filter !in it.filters) null else it.originCity,
            )
        }
        reload(refreshing = false)
    }

    fun setOrigin(city: CityItem?) {
        _state.update { it.copy(originCity = city, filters = if (city != null) it.filters - LoadFilter.Nearby else it.filters) }
        reload(refreshing = false)
    }

    fun setDestination(city: CityItem?) {
        _state.update { it.copy(destinationCity = city) }
        reload(refreshing = false)
    }

    fun setDates(from: LocalDate?, to: LocalDate?) {
        _state.update { it.copy(dateFrom = from, dateTo = to) }
        reload(refreshing = false)
    }

    fun setCargoType(type: CargoType?) {
        _state.update { it.copy(cargoType = type) }
        reload(refreshing = false)
    }

    fun clearFilters() {
        _state.update {
            it.copy(filters = emptySet(), originCity = null, destinationCity = null, dateFrom = null, dateTo = null, cargoType = null)
        }
        reload(refreshing = false)
    }

    /** Primera página con los filtros actuales; cancela cualquier carga en curso. */
    private fun reload(refreshing: Boolean) {
        job?.cancel()
        _state.update { it.copy(loading = !refreshing, refreshing = refreshing, loadingMore = false, error = null) }
        val filter = _state.value.toServerFilter()
        job = viewModelScope.launch {
            loads.marketplacePage(filter, 0, FEED_PAGE_SIZE).fold(
                onSuccess = { p ->
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, items = p.items.distinctBy { r -> r.id },
                            total = p.total, page = p.page, hasMore = p.hasMore,
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    /** Siguiente página (scroll infinito), sin duplicar elementos. */
    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loadingMore || s.loading || s.refreshing) return
        _state.update { it.copy(loadingMore = true) }
        val filter = s.toServerFilter()
        job = viewModelScope.launch {
            loads.marketplacePage(filter, s.page + 1, FEED_PAGE_SIZE).fold(
                onSuccess = { p ->
                    _state.update {
                        val known = it.items.mapTo(HashSet()) { r -> r.id }
                        it.copy(
                            loadingMore = false, items = it.items + p.items.filter { r -> known.add(r.id) },
                            total = p.total, page = p.page, hasMore = p.hasMore,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(loadingMore = false, hasMore = false, message = UiMessage.error(e.userMessage())) }
                },
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

// ───────────────────────── Detalle + ofertar ─────────────────────────

/** Estado del bottom sheet para ofertar. */
data class BidFormState(
    val vehicleId: String? = null,
    val amountText: String = "",
    val notes: String = "",
    val submitting: Boolean = false,
    /** Error general del envío: mensaje de la API tal cual (monto bajo el piso, subasta cerrada, ya ofertaste). */
    val error: String? = null,
    val vehicleError: UiText? = null,
    val amountError: UiText? = null,
    /** Muestra el diálogo "¿Enviar oferta de S/ X?" antes de mandar la oferta. */
    val confirming: Boolean = false,
) {
    val amount: Double? get() = parseAmount(amountText)
}

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
    val message: UiMessage? = null,
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

    fun selectVehicle(id: String) = updateForm { it.copy(vehicleId = id, error = null, vehicleError = null) }
    fun onAmountChange(text: String) = updateForm { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null, amountError = null) }
    fun onNotesChange(text: String) = updateForm { it.copy(notes = text.take(300)) }

    private fun updateForm(f: (BidFormState) -> BidFormState) = _state.update { s -> s.form?.let { s.copy(form = f(it)) } ?: s }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** Valida el formulario y, si es válido, pide confirmación antes de enviar. */
    fun requestConfirm() {
        val s = _state.value
        val form = s.form ?: return
        val request = s.request ?: return
        if (form.submitting) return
        val amount = parseAmount(form.amountText)
        val floor = request.priceFloor
        val (vehicleError, amountError) = validateBid(form, amount, floor)
        if (vehicleError != null || amountError != null) {
            updateForm { it.copy(vehicleError = vehicleError, amountError = amountError, error = null) }
            return
        }
        updateForm { it.copy(confirming = true, error = null) }
    }

    private fun validateBid(form: BidFormState, amount: Double?, floor: Double?): Pair<UiText?, UiText?> {
        val vehicleError = if (form.vehicleId == null) UiText.of(R.string.loads_error_vehicle_required) else null
        val amountError = when {
            amount == null || amount <= 0 -> UiText.of(R.string.loads_error_amount_invalid)
            floor != null && amount < floor -> UiText.of(R.string.loads_error_amount_below_floor, Fmt.money(floor))
            else -> null
        }
        return vehicleError to amountError
    }

    fun dismissConfirm() = updateForm { if (it.submitting) it else it.copy(confirming = false) }

    fun submitBid() {
        val s = _state.value
        val form = s.form ?: return
        val request = s.request ?: return
        if (form.submitting) return
        val amount = parseAmount(form.amountText)
        val floor = request.priceFloor
        val (vehicleError, amountError) = validateBid(form, amount, floor)
        if (vehicleError != null || amountError != null) {
            updateForm { it.copy(vehicleError = vehicleError, amountError = amountError, confirming = false) }
            return
        }
        updateForm { it.copy(submitting = true, error = null, vehicleError = null, amountError = null) }
        viewModelScope.launch {
            loads.placeBid(request.id, form.vehicleId!!, amount!!, form.notes).fold(
                onSuccess = {
                    _state.update { st -> st.copy(form = null, message = UiMessage.success(R.string.loads_bid_sent)) }
                    load(initial = false)
                },
                // Se muestra el mensaje de la API (p. ej. monto bajo el piso, subasta cerrada, ya ofertaste).
                onFailure = { e ->
                    val field = (e as? ApiException)?.field
                    updateForm {
                        when (field) {
                            "amount" -> it.copy(submitting = false, confirming = false, amountError = UiText.Raw(e.userMessage()))
                            "vehicleId" -> it.copy(submitting = false, confirming = false, vehicleError = UiText.Raw(e.userMessage()))
                            else -> it.copy(submitting = false, confirming = false, error = e.userMessage())
                        }
                    }
                },
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
