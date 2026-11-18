package pe.logyx.feature.loads

import pe.logyx.core.data.RoutesRepository
import pe.logyx.core.data.between
import pe.logyx.core.model.RouteGeometry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.apiCode
import pe.logyx.core.data.fieldErrors
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.VehicleChoice
import pe.logyx.core.designsystem.components.moneyToInput
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import pe.logyx.core.model.BidOutcome
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.CounterOfferStatus
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.DashboardSummary
import pe.logyx.core.model.MarketplaceFilter
import pe.logyx.core.model.MarketplaceSort
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.ReturnLoadHintView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.VehicleView
import pe.logyx.core.model.canCarry
import pe.logyx.core.model.canManageMyBid
import pe.logyx.core.model.fitFor
import pe.logyx.core.model.isPendingFor
import pe.logyx.core.model.myBidOutcome
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
    // Fase 4: viajes publicados, Smart Load y cargas de retorno (null = el API no los envió)
    val openTrips: Int? = null,
    val pendingTripBookings: Int? = null,
    val pendingSmartLoads: Int? = null,
    val returnLoadCount: Int? = null,
    val topReturnLoads: List<ReturnLoadHintView> = emptyList(),
)

class CarrierHomeViewModel(
    private val auth: AuthRepository,
    private val loads: LoadsRepository,
    private val shipments: ShipmentsRepository,
    private val realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CarrierHomeUiState(userName = auth.currentUser()?.fullName.orEmpty()))
    val state: StateFlow<CarrierHomeUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano): subasta (`auction.updated`) y resultado de mis ofertas refrescan en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(CARRIER_AUCTION_TYPES).collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.suggested.isEmpty(), refreshing = !initial, error = null) }
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
                            openTrips = summary?.openTrips,
                            pendingTripBookings = summary?.pendingTripBookings,
                            pendingSmartLoads = summary?.pendingSmartLoads,
                            returnLoadCount = summary?.returnLoads,
                            topReturnLoads = summary?.topReturnLoads.orEmpty(),
                            totalBids = myBids.size,
                            activeShipment = active,
                            vehicles = vehicles,
                        )
                    }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

/** Eventos de subasta que ve el transportista: `auction.updated`, `bid.accepted`, `bid.rejected` (+ contraofertas). */
private val CARRIER_AUCTION_TYPES = setOf(
    RealtimeTypes.AUCTION_UPDATED, RealtimeTypes.BID_ACCEPTED, RealtimeTypes.BID_REJECTED, RealtimeTypes.COUNTER_OFFER_UPDATED,
)

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
    private val realtime: RealtimeRepository,
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

    /**
     * Tiempo real (solo en primer plano): recarga la primera página en silencio. Si el usuario ya paginó (`page > 0`)
     * no se le salta la lista; ahí manda el pull-to-refresh.
     */
    suspend fun liveLoop() {
        realtime.refreshSignals(CARRIER_AUCTION_TYPES).collect {
            val s = _state.value
            if (!s.loading && !s.refreshing && !s.loadingMore && s.page == 0) reload(refreshing = false, silent = true)
        }
    }

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
    private fun reload(refreshing: Boolean, silent: Boolean = false) {
        job?.cancel()
        if (!silent) _state.update { it.copy(loading = !refreshing, refreshing = refreshing, loadingMore = false, error = null) }
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
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
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

/** Largos máximos alineados con los `@Size` del backend (F37): nota de la oferta 300, mensaje de la contraoferta 500. */
internal const val MAX_BID_NOTES = 300
internal const val MAX_COUNTER_MESSAGE = 500

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
    /** RN07 (`NEG-001`, field `notes`): la nota lleva teléfono, correo o enlace. */
    val notesError: UiText? = null,
    /** Muestra el diálogo "¿Enviar oferta de S/ X?" antes de mandar la oferta. */
    val confirming: Boolean = false,
) {
    val amount: Double? get() = parseAmount(amountText)
}

/** Hoja "Contraofertar" del transportista (RF26): monto y mensaje con sus errores de campo (`amount`, `message` RN07). */
data class CarrierCounterForm(
    val amount: String = "",
    val message: String = "",
    val amountError: UiText? = null,
    val messageError: UiText? = null,
    val confirming: Boolean = false,
    val sending: Boolean = false,
)

data class LoadDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val request: RequestView? = null,
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Ruta por carretera para el mapa (ADR-0008); null = cargando o sin red (el mapa dibuja la recta). */
    val route: RouteGeometry? = null,
    val vehicles: List<VehicleView> = emptyList(),
    val form: BidFormState? = null,
    /** Mensaje efímero (snackbar); la UI lo consume con [LoadDetailViewModel.messageShown]. */
    val message: UiMessage? = null,
    /** Contraofertas de mi oferta, recientes primero (V13: la PYME o yo). */
    val counters: List<CounterOfferView> = emptyList(),
    /** Aceptar o rechazar la contraoferta de la PYME: espera confirmación. */
    val counterConfirm: CounterAction? = null,
    val counterForm: CarrierCounterForm? = null,
    val confirmWithdraw: Boolean = false,
    /** Acción de escritura en curso (aceptar/rechazar contraoferta o retirar). */
    val busy: Boolean = false,
) {
    val compatibleVehicles: List<VehicleView>
        get() = request?.let { r -> vehicles.filter { it.canCarry(r) } } ?: emptyList()

    /** Toda la flota con su veredicto RN02: los no aptos se muestran deshabilitados con el motivo (F09). */
    val vehicleChoices: List<VehicleChoice>
        get() = request?.let { r -> vehicles.map { VehicleChoice(it, it.fitFor(r)) } } ?: emptyList()

    /** Contraoferta de la PYME que espera mi respuesta. */
    val counterFromPyme: CounterOfferView? get() = counters.firstOrNull { it.isPendingFor(viewerIsCarrier = true) }

    /** Contraoferta mía que espera la respuesta de la PYME. */
    val myPendingCounter: CounterOfferView? get() = counters.firstOrNull { it.status == CounterOfferStatus.PENDING && it.proposedByCarrier }

    /** Retirar solo mientras la subasta acepta ofertas (`BID-005`). */
    val canWithdraw: Boolean get() = request?.canManageMyBid == true

    /** Contraofertar: oferta pendiente de una solicitud publicada (la subasta puede haber cerrado). */
    val canCounter: Boolean get() = request?.isBidActive == true
}

class LoadDetailViewModel(
    private val requestId: String,
    private val loads: LoadsRepository,
    private val catalog: CatalogRepository,
    private val negotiation: NegotiationRepository,
    private val realtime: RealtimeRepository,
    private val routes: RoutesRepository = RoutesRepository.NONE,
) : ViewModel() {
    private val _state = MutableStateFlow(LoadDetailUiState())
    val state: StateFlow<LoadDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano): `auction.updated`/`bid.*` de ESTA carga refrescan el detalle en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(CARRIER_AUCTION_TYPES) { it.string("requestId") == requestId }
            .collect { if (!_state.value.isWriting) load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.request == null, refreshing = !initial, error = null) }
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
                    routes.between(r.originCity, r.destinationCity)?.let { g -> _state.update { it.copy(route = g) } }
                    loadCounters(r)
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    /** Solo una oferta pendiente puede tener contraofertas vivas; si no, se limpian. */
    private suspend fun loadCounters(r: RequestView) {
        val bid = r.myBid
        if (bid == null || bid.status != BidStatus.PENDING) {
            _state.update { it.copy(counters = emptyList()) }
            return
        }
        negotiation.counterOffers(bid.id).onSuccess { list ->
            _state.update { it.copy(counters = list.sortedByDescending { c -> c.createdAt.orEmpty() }) }
        }
    }

    private val LoadDetailUiState.isWriting: Boolean
        get() = busy || form?.submitting == true || counterForm?.sending == true

    private fun List<CityItem>.find(code: String) = firstOrNull { normalizeCity(it.code) == normalizeCity(code) }

    fun openBidSheet() {
        val s = _state.value
        // Con un solo vehículo apto se preselecciona; con varios (o ninguno) el transportista elige.
        val only = s.compatibleVehicles.singleOrNull()
        _state.update { it.copy(form = BidFormState(vehicleId = only?.id)) }
    }

    fun dismissBidSheet() = _state.update { if (it.form?.submitting == true) it else it.copy(form = null) }

    /** Solo se elige un vehículo apto (RN02); los demás aparecen deshabilitados en la hoja. */
    fun selectVehicle(id: String) {
        if (_state.value.compatibleVehicles.none { it.id == id }) return
        updateForm { it.copy(vehicleId = id, error = null, vehicleError = null) }
    }
    fun onAmountChange(text: String) = updateForm { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null, amountError = null) }
    fun onNotesChange(text: String) = updateForm { it.copy(notes = text.take(MAX_BID_NOTES), notesError = null) }

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
                    // Errores de campo: monto (RN03), vehículo (RN02 CAP-001..005) y nota (RN07 NEG-001) van bajo su campo.
                    val fe = e.fieldErrors("amount", "vehicleId", "notes")
                    val capCode = e.apiCode?.startsWith("LOGYX-CAP-") == true
                    updateForm {
                        val base = it.copy(submitting = false, confirming = false)
                        when {
                            fe["amount"] != null -> base.copy(amountError = UiText.Raw(e.userMessage()))
                            fe["vehicleId"] != null || capCode -> base.copy(vehicleError = UiText.Raw(e.userMessage()))
                            fe["notes"] != null -> base.copy(notesError = UiText.Raw(e.userMessage()))
                            else -> base.copy(error = e.userMessage())
                        }
                    }
                },
            )
        }
    }

    // ── Mi oferta: contraofertas y retiro (V13, F08) ──

    fun askAcceptCounter(c: CounterOfferView) = _state.update { it.copy(counterConfirm = CounterAction(c, accept = true)) }
    fun askRejectCounter(c: CounterOfferView) = _state.update { it.copy(counterConfirm = CounterAction(c, accept = false)) }
    fun dismissCounterConfirm() = _state.update { if (it.busy) it else it.copy(counterConfirm = null) }

    /** Acepta o rechaza la contraoferta de la PYME (responde quien la recibió). */
    fun confirmCounterAnswer() {
        val action = _state.value.counterConfirm ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = if (action.accept) negotiation.accept(action.counter.id) else negotiation.reject(action.counter.id)
            result.fold(
                onSuccess = {
                    val msg = if (action.accept) UiMessage.success(R.string.loads_counter_accepted, Fmt.money(action.counter.amount))
                    else UiMessage.success(R.string.loads_counter_rejected)
                    _state.update { it.copy(busy = false, counterConfirm = null, message = msg) }
                    load(initial = false, silent = true)
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, counterConfirm = null, message = UiMessage.error(e.userMessage())) }
                    load(initial = false, silent = true)
                },
            )
        }
    }

    fun openCounterSheet() {
        val bid = _state.value.request?.myBid ?: return
        _state.update { it.copy(counterForm = CarrierCounterForm(amount = moneyToInput(bid.amount))) }
    }

    fun closeCounterSheet() = _state.update { if (it.counterForm?.sending == true) it else it.copy(counterForm = null) }
    fun onCounterAmount(text: String) = updateCounterForm { it.copy(amount = text, amountError = null) }
    fun onCounterMessage(text: String) = updateCounterForm { it.copy(message = text.take(MAX_COUNTER_MESSAGE), messageError = null) }
    fun cancelCounterSendConfirm() = updateCounterForm { if (it.sending) it else it.copy(confirming = false) }

    private fun updateCounterForm(f: (CarrierCounterForm) -> CarrierCounterForm) =
        _state.update { s -> s.counterForm?.let { s.copy(counterForm = f(it)) } ?: s }

    /** Valida (>= piso RN03 y distinto de mi oferta actual) y pide confirmación. */
    fun askConfirmCounterSend() {
        val s = _state.value
        val form = s.counterForm ?: return
        if (form.sending) return
        val request = s.request ?: return
        val bid = request.myBid ?: return
        val amount = parseMoney(form.amount)
        val floor = request.priceFloor
        val error = when {
            amount == null || amount <= 0.0 -> UiText.of(R.string.loads_counter_amount_required)
            floor != null && amount < floor -> UiText.of(R.string.loads_error_amount_below_floor, Fmt.money(floor))
            amount == bid.amount -> UiText.of(R.string.loads_counter_same_amount, Fmt.money(bid.amount))
            else -> null
        }
        if (error != null) updateCounterForm { it.copy(amountError = error) } else updateCounterForm { it.copy(confirming = true) }
    }

    fun submitCounter() {
        val s = _state.value
        val form = s.counterForm ?: return
        val bid = s.request?.myBid ?: return
        val amount = parseMoney(form.amount) ?: return
        if (form.sending) return
        updateCounterForm { it.copy(sending = true) }
        viewModelScope.launch {
            negotiation.propose(bid.id, amount, form.message.trim().takeIf { it.isNotEmpty() }).fold(
                onSuccess = {
                    _state.update { it.copy(counterForm = null, message = UiMessage.success(R.string.loads_counter_sent)) }
                    load(initial = false, silent = true)
                },
                onFailure = { e ->
                    val fe = e.fieldErrors("amount", "message")
                    when {
                        fe["amount"] != null -> updateCounterForm { it.copy(sending = false, confirming = false, amountError = UiText.Raw(e.userMessage())) }
                        // RN07 (`NEG-001`) bajo el campo del mensaje.
                        fe["message"] != null -> updateCounterForm { it.copy(sending = false, confirming = false, messageError = UiText.Raw(e.userMessage())) }
                        else -> {
                            // NEG-004 (ya hay una pendiente tuya), acceso (ORG/AUTH) y demás: mensaje, y se refresca por si cambió.
                            updateCounterForm { it.copy(sending = false, confirming = false) }
                            _state.update { it.copy(message = UiMessage.error(e.userMessage())) }
                            load(initial = false, silent = true)
                        }
                    }
                },
            )
        }
    }

    fun askWithdraw() = _state.update { it.copy(confirmWithdraw = true) }
    fun dismissWithdraw() = _state.update { if (it.busy) it else it.copy(confirmWithdraw = false) }

    /** Retira mi oferta pendiente (`BID-005` si la subasta ya no admite cambios). No se puede volver a ofertar (`BID-003`). */
    fun confirmWithdraw() {
        val bid = _state.value.request?.myBid ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            loads.withdrawBid(bid.id).fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, confirmWithdraw = false, message = UiMessage.success(R.string.loads_withdrawn)) }
                    load(initial = false, silent = true)
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, confirmWithdraw = false, message = UiMessage.error(e.userMessage())) }
                    load(initial = false, silent = true)
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

/** Acción sobre una contraoferta pendiente de confirmar. */
data class CounterAction(val counter: CounterOfferView, val accept: Boolean)

/** Filtro de "Mis ofertas": las vivas (subasta abierta o esperando a la PYME) o las ya resueltas (ganadas, perdidas, canceladas, retiradas). */
enum class MyBidsFilter { All, Active, Closed }

/** La oferta sigue viva: puede cambiar de estado (F15). */
val RequestView.isBidActive: Boolean
    get() = myBidOutcome.let { it == BidOutcome.OPEN || it == BidOutcome.AWAITING_DECISION }

data class MyBidsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Todas las solicitudes donde ofertó (F15: incluye perdidas, canceladas y retiradas); activas primero. */
    val items: List<RequestView> = emptyList(),
    val filter: MyBidsFilter = MyBidsFilter.All,
    /** Contraoferta PENDING (de la PYME o mía) por id de oferta (RF26/RF27). */
    val counters: Map<String, CounterOfferView> = emptyMap(),
    val confirm: CounterAction? = null,
    val busy: Boolean = false,
    val message: UiMessage? = null,
) {
    val visible: List<RequestView>
        get() = when (filter) {
            MyBidsFilter.All -> items
            MyBidsFilter.Active -> items.filter { it.isBidActive }
            MyBidsFilter.Closed -> items.filterNot { it.isBidActive }
        }
    val activeCount: Int get() = items.count { it.isBidActive }
}

/** Activas primero; dentro de cada grupo, la oferta más reciente primero. */
internal fun sortMyBids(list: List<RequestView>): List<RequestView> =
    list.sortedWith(compareByDescending<RequestView> { it.isBidActive }.thenByDescending { r -> r.myBid?.createdAt.orEmpty() })

class MyBidsViewModel(
    private val loads: LoadsRepository,
    private val negotiation: NegotiationRepository,
    private val realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MyBidsUiState())
    val state: StateFlow<MyBidsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano): `bid.accepted`/`bid.rejected`/contraofertas refrescan mis ofertas en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(CARRIER_AUCTION_TYPES).collect { if (!_state.value.busy) load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            loads.myBids().fold(
                onSuccess = { list ->
                    val sorted = sortMyBids(list)
                    _state.update { it.copy(loading = false, refreshing = false, items = sorted) }
                    loadCounters(sorted)
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    /** Solo consulta las ofertas vivas (las demás ya no admiten contraoferta), con concurrencia limitada. */
    private suspend fun loadCounters(items: List<RequestView>) {
        val gate = Semaphore(4)
        val pending = items.filter { it.isBidActive }.mapNotNull { it.myBid }
        val found = coroutineScope {
            pending.map { bid ->
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

    fun setFilter(filter: MyBidsFilter) = _state.update { it.copy(filter = filter) }
    fun askAccept(c: CounterOfferView) = _state.update { it.copy(confirm = CounterAction(c, accept = true)) }
    fun askReject(c: CounterOfferView) = _state.update { it.copy(confirm = CounterAction(c, accept = false)) }
    fun dismissConfirm() = _state.update { if (it.busy) it else it.copy(confirm = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun confirm() {
        val action = _state.value.confirm ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = if (action.accept) negotiation.accept(action.counter.id) else negotiation.reject(action.counter.id)
            result.fold(
                onSuccess = {
                    val msg = if (action.accept) UiMessage.success(R.string.loads_counter_accepted, Fmt.money(action.counter.amount))
                    else UiMessage.success(R.string.loads_counter_rejected)
                    _state.update { it.copy(busy = false, confirm = null, message = msg) }
                    load(initial = false)
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, confirm = null, message = UiMessage.error(e.userMessage())) }
                    load(initial = false)
                },
            )
        }
    }
}
