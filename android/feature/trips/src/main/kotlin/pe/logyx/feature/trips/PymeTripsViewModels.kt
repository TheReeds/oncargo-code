package pe.logyx.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.TripsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.CreateTripBookingRequest
import pe.logyx.core.model.TripBookingView
import pe.logyx.core.model.TripFilter
import pe.logyx.core.model.TripView
import pe.logyx.core.model.priceFor
import pe.logyx.core.network.ApiException
import java.time.LocalDate

// ───────────────────────── Buscar viajes y reservar (PYME) ─────────────────────────

/** Formulario de reserva sobre un [trip]. */
data class BookFormState(
    val trip: TripView,
    val weightText: String = "",
    val description: String = "",
    val cargoType: CargoType? = null,
    val pickup: String = "",
    val delivery: String = "",
    val weightError: UiText? = null,
    val descriptionError: UiText? = null,
    val cargoTypeError: UiText? = null,
    val pickupError: UiText? = null,
    val deliveryError: UiText? = null,
    val confirming: Boolean = false,
    val submitting: Boolean = false,
) {
    val weightKg: Double? get() = parseKg(weightText)
    /** Precio estimado con la misma fórmula del backend; null mientras no haya un peso válido. */
    val estimatedPrice: Double? get() = weightKg?.takeIf { it > 0 }?.let { trip.priceFor(it) }
}

data class TripSearchUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val items: List<TripView> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val cities: List<CityItem> = emptyList(),
    val origin: CityItem? = null,
    val destination: CityItem? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val minWeightText: String = "",
    val book: BookFormState? = null,
    /** Sube cada vez que una reserva se envía con éxito (la pantalla salta a "Mis reservas"). */
    val bookedTick: Int = 0,
    val message: UiMessage? = null,
) {
    val filter: TripFilter
        get() = TripFilter(
            originCity = origin?.code, destinationCity = destination?.code,
            fromDate = fromDate?.toString(), toDate = (toDate ?: fromDate)?.toString(),
            minWeightKg = parseKg(minWeightText)?.takeIf { it > 0 },
        )
    val hasMore: Boolean get() = items.size < total
}

/** Valida el formulario de reserva; devuelve el estado con los errores marcados. */
internal fun BookFormState.validated(): BookFormState {
    val w = weightKg
    return copy(
        weightError = when {
            w == null || w <= 0 -> UiText.of(R.string.trips_book_err_weight)
            w > trip.remainingKg -> UiText.of(R.string.trips_book_err_weight_max, kgText(trip.remainingKg))
            w > MAX_TRIP_KG -> UiText.of(R.string.trips_err_capacity_max, kgText(MAX_TRIP_KG))
            else -> null
        },
        descriptionError = if (description.isBlank()) UiText.of(R.string.trips_book_err_description) else null,
        cargoTypeError = if (cargoType == null) UiText.of(R.string.trips_book_err_cargo_type) else null,
        pickupError = if (pickup.isBlank()) UiText.of(R.string.trips_book_err_pickup) else null,
        deliveryError = if (delivery.isBlank()) UiText.of(R.string.trips_book_err_delivery) else null,
    )
}

internal val BookFormState.hasErrors: Boolean
    get() = listOf(weightError, descriptionError, cargoTypeError, pickupError, deliveryError).any { it != null }

class TripSearchViewModel(
    private val trips: TripsRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TripSearchUiState())
    val state: StateFlow<TripSearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { catalog.cities().onSuccess { c -> _state.update { it.copy(cities = c) } } }
        reload(initial = true)
    }

    fun refresh() = reload(initial = false)

    private fun reload(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        val filter = _state.value.filter
        viewModelScope.launch {
            trips.search(filter, page = 0).fold(
                onSuccess = { p ->
                    // Ignora respuestas de filtros ya reemplazados.
                    if (filter == _state.value.filter) {
                        _state.update { it.copy(loading = false, refreshing = false, items = p.items, total = p.total, page = 0) }
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.refreshing || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        val filter = s.filter
        viewModelScope.launch {
            trips.search(filter, page = s.page + 1).fold(
                onSuccess = { p ->
                    _state.update {
                        if (filter != it.filter) it.copy(loadingMore = false)
                        else it.copy(loadingMore = false, items = it.items + p.items, total = p.total, page = p.page)
                    }
                },
                onFailure = { e -> _state.update { it.copy(loadingMore = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    /** Aplica todos los filtros de la hoja de una vez. */
    fun applyFilters(origin: CityItem?, destination: CityItem?, from: LocalDate?, to: LocalDate?, minWeightText: String) {
        _state.update { it.copy(origin = origin, destination = destination, fromDate = from, toDate = to, minWeightText = minWeightText) }
        reload(initial = true)
    }

    fun clearFilters() = applyFilters(null, null, null, null, "")

    // --- Reserva ---
    fun openBook(trip: TripView) = _state.update { it.copy(book = BookFormState(trip)) }
    fun dismissBook() = _state.update { if (it.book?.submitting == true) it else it.copy(book = null) }
    fun dismissConfirm() = updateBook { if (it.submitting) it else it.copy(confirming = false) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun updateBook(f: (BookFormState) -> BookFormState) = _state.update { s -> s.book?.let { s.copy(book = f(it)) } ?: s }

    fun onWeight(text: String) = updateBook { it.copy(weightText = sanitizeKgInput(text), weightError = null) }
    fun onDescription(text: String) = updateBook { it.copy(description = text.take(300), descriptionError = null) }
    fun onCargoType(type: CargoType) = updateBook { it.copy(cargoType = type, cargoTypeError = null) }
    fun onPickup(text: String) = updateBook { it.copy(pickup = text.take(200), pickupError = null) }
    fun onDelivery(text: String) = updateBook { it.copy(delivery = text.take(200), deliveryError = null) }

    /** Valida el formulario y, si es válido, pide confirmación antes de reservar. */
    fun requestConfirm() {
        val form = _state.value.book ?: return
        if (form.submitting) return
        val checked = form.validated()
        updateBook { checked.copy(confirming = !checked.hasErrors) }
    }

    fun submit() {
        val form = _state.value.book ?: return
        if (form.submitting) return
        val checked = form.validated()
        if (checked.hasErrors) {
            updateBook { checked.copy(confirming = false) }
            return
        }
        updateBook { it.copy(submitting = true) }
        val body = CreateTripBookingRequest(
            weightKg = checked.weightKg!!, cargoDescription = checked.description.trim(), cargoType = checked.cargoType!!,
            pickupAddress = checked.pickup.trim(), deliveryAddress = checked.delivery.trim(),
        )
        viewModelScope.launch {
            trips.book(form.trip.id, body).fold(
                onSuccess = {
                    _state.update { it.copy(book = null, bookedTick = it.bookedTick + 1, message = UiMessage.success(R.string.trips_book_sent)) }
                    reload(initial = false)
                },
                onFailure = { e ->
                    val raw = UiText.Raw(e.userMessage())
                    updateBook {
                        val base = it.copy(submitting = false, confirming = false)
                        when ((e as? ApiException)?.field) {
                            "weightKg" -> base.copy(weightError = raw)
                            "cargoType" -> base.copy(cargoTypeError = raw)
                            else -> base
                        }
                    }
                    if ((e as? ApiException)?.field !in setOf("weightKg", "cargoType")) {
                        _state.update { it.copy(message = UiMessage.error(e.userMessage())) }
                    }
                },
            )
        }
    }
}

// ───────────────────────── Mis reservas (PYME) ─────────────────────────

data class MyBookingsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val bookings: List<TripBookingView> = emptyList(),
    val pendingCancel: TripBookingView? = null,
    val busy: Boolean = false,
    val message: UiMessage? = null,
) {
    /** Reservas que siguen vivas (por confirmar o confirmadas). */
    val activeCount: Int get() = bookings.count { it.status == BookingStatus.PENDING || it.status == BookingStatus.CONFIRMED }
}

class MyBookingsViewModel(private val trips: TripsRepository) : ViewModel() {
    private val _state = MutableStateFlow(MyBookingsUiState())
    val state: StateFlow<MyBookingsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.bookings.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            trips.myBookings().fold(
                onSuccess = { list ->
                    // Vivas primero; dentro de cada grupo, las más recientes.
                    val sorted = list.sortedWith(
                        compareBy<TripBookingView> { it.status != BookingStatus.PENDING && it.status != BookingStatus.CONFIRMED }
                            .thenByDescending { it.createdAt.orEmpty() },
                    )
                    _state.update { it.copy(loading = false, refreshing = false, bookings = sorted) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun askCancel(b: TripBookingView) = _state.update { if (it.busy) it else it.copy(pendingCancel = b) }
    fun dismissCancel() = _state.update { if (it.busy) it else it.copy(pendingCancel = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun confirmCancel() {
        val b = _state.value.pendingCancel ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            trips.cancelBooking(b.id).fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, pendingCancel = null, message = UiMessage.success(R.string.trips_booking_cancelled)) }
                    load(initial = false)
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, pendingCancel = null, message = UiMessage.error(e.userMessage())) }
                    load(initial = false)
                },
            )
        }
    }
}
