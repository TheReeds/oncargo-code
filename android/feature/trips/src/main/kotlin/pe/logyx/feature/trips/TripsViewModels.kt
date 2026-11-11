package pe.logyx.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.TripsRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.CreateTripRequest
import pe.logyx.core.model.TripBookingView
import pe.logyx.core.model.TripPricingMode
import pe.logyx.core.model.TripView
import pe.logyx.core.model.VehicleView
import pe.logyx.core.model.isManageable
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Hora de Lima (UTC-5, sin horario de verano): la salida se publica con este offset. */
internal val LIMA: ZoneOffset = ZoneOffset.ofHours(-5)

// ───────────────────────── Publicar viaje (transportista) ─────────────────────────

data class PublishTripForm(
    val origin: CityItem? = null,
    val destination: CityItem? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val vehicleId: String? = null,
    val capacityText: String = "",
    val pricingMode: TripPricingMode = TripPricingMode.PER_KG,
    val rateText: String = "",
    val notes: String = "",
) {
    /** Salida en hora de Lima, o null si falta fecha u hora. */
    val departure: OffsetDateTime?
        get() = if (date != null && time != null) LocalDateTime.of(date, time).atOffset(LIMA) else null
}

data class PublishTripErrors(
    val origin: UiText? = null,
    val destination: UiText? = null,
    val departure: UiText? = null,
    val vehicle: UiText? = null,
    val capacity: UiText? = null,
    val rate: UiText? = null,
) {
    val any: Boolean get() = listOf(origin, destination, departure, vehicle, capacity, rate).any { it != null }
}

data class PublishTripUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val cities: List<CityItem> = emptyList(),
    val vehicles: List<VehicleView> = emptyList(),
    val form: PublishTripForm = PublishTripForm(),
    val errors: PublishTripErrors = PublishTripErrors(),
    val submitting: Boolean = false,
    val message: UiMessage? = null,
    /** Id del viaje publicado: la pantalla navega al detalle. */
    val publishedId: String? = null,
) {
    val selectedVehicle: VehicleView? get() = vehicles.firstOrNull { it.id == form.vehicleId }
}

/** Validación inline del formulario (pura para poder probarla con una hora fija). */
internal fun validateTrip(form: PublishTripForm, vehicles: List<VehicleView>, now: OffsetDateTime): PublishTripErrors {
    val vehicle = vehicles.firstOrNull { it.id == form.vehicleId }
    val capacity = parseKg(form.capacityText)
    val rate = parseMoney(form.rateText)
    val departure = form.departure
    return PublishTripErrors(
        origin = if (form.origin == null) UiText.of(R.string.trips_err_origin) else null,
        destination = when {
            form.destination == null -> UiText.of(R.string.trips_err_destination)
            form.destination.code == form.origin?.code -> UiText.of(R.string.trips_err_same_city)
            else -> null
        },
        departure = when {
            departure == null -> UiText.of(R.string.trips_err_departure_missing)
            !departure.isAfter(now) -> UiText.of(R.string.trips_err_departure_past)
            else -> null
        },
        vehicle = if (vehicle == null) UiText.of(R.string.trips_err_vehicle) else null,
        capacity = when {
            capacity == null || capacity <= 0 -> UiText.of(R.string.trips_err_capacity)
            capacity > MAX_TRIP_KG -> UiText.of(R.string.trips_err_capacity_max, kgText(MAX_TRIP_KG))
            vehicle != null && capacity > vehicle.maxWeightKg ->
                UiText.of(R.string.trips_err_capacity_vehicle, vehicle.plate, kgText(vehicle.maxWeightKg))
            else -> null
        },
        rate = when {
            rate == null || rate <= 0 -> UiText.of(R.string.trips_err_rate)
            rate > MAX_RATE -> UiText.of(R.string.trips_err_rate_max, Fmt.money(MAX_RATE))
            else -> null
        },
    )
}

class PublishTripViewModel(
    private val trips: TripsRepository,
    private val loads: LoadsRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PublishTripUiState())
    val state: StateFlow<PublishTripUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val cities = async { catalog.cities() }
            val fleet = loads.vehicles()
            fleet.fold(
                onSuccess = { vehicles ->
                    _state.update { s ->
                        s.copy(
                            loading = false, vehicles = vehicles, cities = cities.await().getOrDefault(emptyList()),
                            form = s.form.copy(vehicleId = s.form.vehicleId ?: vehicles.singleOrNull()?.id),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    private fun edit(clear: (PublishTripErrors) -> PublishTripErrors = { it }, f: (PublishTripForm) -> PublishTripForm) =
        _state.update { it.copy(form = f(it.form), errors = clear(it.errors)) }

    fun onOrigin(c: CityItem?) = edit({ it.copy(origin = null, destination = null) }) { it.copy(origin = c) }
    fun onDestination(c: CityItem?) = edit({ it.copy(destination = null) }) { it.copy(destination = c) }
    fun onDate(d: LocalDate) = edit({ it.copy(departure = null) }) { it.copy(date = d) }
    fun onTime(t: LocalTime) = edit({ it.copy(departure = null) }) { it.copy(time = t) }
    fun onVehicle(id: String) = edit({ it.copy(vehicle = null, capacity = null) }) { it.copy(vehicleId = id) }
    fun onCapacity(text: String) = edit({ it.copy(capacity = null) }) { it.copy(capacityText = sanitizeKgInput(text)) }
    fun onPricingMode(mode: TripPricingMode) = edit({ it.copy(rate = null) }) { it.copy(pricingMode = mode) }
    fun onRate(text: String) = edit({ it.copy(rate = null) }) { it.copy(rateText = text) }
    fun onNotes(text: String) = edit { it.copy(notes = text.take(300)) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun submit(now: OffsetDateTime = OffsetDateTime.now()) {
        val s = _state.value
        if (s.submitting) return
        val errors = validateTrip(s.form, s.vehicles, now)
        if (errors.any) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val f = s.form
        val body = CreateTripRequest(
            originCity = f.origin!!.code,
            destinationCity = f.destination!!.code,
            departureAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(f.departure!!),
            vehicleId = f.vehicleId!!,
            capacityKg = parseKg(f.capacityText)!!,
            pricingMode = f.pricingMode,
            rate = parseMoney(f.rateText)!!,
            notes = f.notes.trim().takeIf { it.isNotEmpty() },
        )
        _state.update { it.copy(submitting = true, errors = PublishTripErrors()) }
        viewModelScope.launch {
            trips.create(body).fold(
                onSuccess = { trip -> _state.update { it.copy(submitting = false, publishedId = trip.id) } },
                onFailure = { e ->
                    val raw = UiText.Raw(e.userMessage())
                    _state.update {
                        when ((e as? ApiException)?.field) {
                            "capacityKg" -> it.copy(submitting = false, errors = it.errors.copy(capacity = raw))
                            "departureAt" -> it.copy(submitting = false, errors = it.errors.copy(departure = raw))
                            "vehicleId" -> it.copy(submitting = false, errors = it.errors.copy(vehicle = raw))
                            "rate" -> it.copy(submitting = false, errors = it.errors.copy(rate = raw))
                            else -> it.copy(submitting = false, message = UiMessage.error(e.userMessage()))
                        }
                    }
                },
            )
        }
    }
}

// ───────────────────────── Mis viajes (transportista) ─────────────────────────

data class CarrierTripsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val trips: List<TripView> = emptyList(),
)

class CarrierTripsViewModel(private val trips: TripsRepository, private val realtime: RealtimeRepository) : ViewModel() {
    private val _state = MutableStateFlow(CarrierTripsUiState())
    val state: StateFlow<CarrierTripsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano, lo lanza la UI): `trip_booking.updated`/`trip.cancelled` refresca en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(setOf(RealtimeTypes.TRIP_BOOKING_UPDATED, RealtimeTypes.TRIP_CANCELLED)).collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.trips.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            trips.myTrips().fold(
                onSuccess = { list ->
                    // Primero los que aún admiten gestión, por salida más próxima; luego el historial.
                    val sorted = list.sortedWith(compareBy<TripView>({ !it.isManageable }, { it.departureAt }))
                    _state.update { it.copy(loading = false, refreshing = false, trips = sorted) }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

// ───────────────────────── Detalle del viaje + reservas (transportista) ─────────────────────────

/** Acción pendiente de confirmar en un diálogo. */
sealed interface TripAction {
    data class Confirm(val booking: TripBookingView) : TripAction
    data class Reject(val booking: TripBookingView) : TripAction
    data object Close : TripAction
    data object Cancel : TripAction
}

data class TripDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val trip: TripView? = null,
    val bookings: List<TripBookingView> = emptyList(),
    val pending: TripAction? = null,
    /** Motivo opcional al rechazar una reserva (máx. [MAX_REJECT_REASON]). */
    val rejectReason: String = "",
    val busy: Boolean = false,
    val message: UiMessage? = null,
)

internal const val MAX_REJECT_REASON = 300

class CarrierTripDetailViewModel(
    private val tripId: String,
    private val justPublished: Boolean,
    private val trips: TripsRepository,
    private val realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TripDetailUiState(message = if (justPublished) UiMessage.success(R.string.trips_published) else null),
    )
    val state: StateFlow<TripDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano, lo lanza la UI): `trip_booking.updated`/`trip.cancelled` de ESTE viaje refresca en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(setOf(RealtimeTypes.TRIP_BOOKING_UPDATED, RealtimeTypes.TRIP_CANCELLED)) { it.string("tripId") == tripId }.collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.trip == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val tripRes = async { trips.trip(tripId) }
            val bookingsRes = async { trips.bookings(tripId) }
            tripRes.await().fold(
                onSuccess = { t ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, trip = t, bookings = bookingsRes.await().getOrDefault(it.bookings).sortedForCarrier())
                    }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun askConfirm(b: TripBookingView) = ask(TripAction.Confirm(b))
    fun askReject(b: TripBookingView) = ask(TripAction.Reject(b))
    fun askClose() = ask(TripAction.Close)
    fun askCancel() = ask(TripAction.Cancel)
    private fun ask(a: TripAction) = _state.update { if (it.busy) it else it.copy(pending = a, rejectReason = "") }
    fun dismissConfirm() = _state.update { if (it.busy) it else it.copy(pending = null, rejectReason = "") }
    fun onRejectReason(text: String) = _state.update { it.copy(rejectReason = text.take(MAX_REJECT_REASON)) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun perform() {
        val action = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val (result, success) = when (action) {
                is TripAction.Confirm -> trips.confirm(action.booking.id) to R.string.trips_booking_confirmed
                is TripAction.Reject -> trips.reject(action.booking.id, _state.value.rejectReason.trim().ifEmpty { null }) to R.string.trips_booking_rejected
                TripAction.Close -> trips.close(tripId) to R.string.trips_trip_closed
                TripAction.Cancel -> trips.cancel(tripId) to R.string.trips_trip_cancelled
            }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, pending = null, rejectReason = "", message = UiMessage.success(success)) }
                    load(initial = false)
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, pending = null, rejectReason = "", message = UiMessage.error(e.userMessage())) }
                    load(initial = false)
                },
            )
        }
    }
}

/** Pendientes primero (requieren acción), luego las más recientes. */
internal fun List<TripBookingView>.sortedForCarrier(): List<TripBookingView> =
    sortedWith(compareBy<TripBookingView> { it.status != BookingStatus.PENDING }.thenByDescending { it.createdAt.orEmpty() })
