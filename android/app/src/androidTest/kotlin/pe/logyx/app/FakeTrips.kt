package pe.logyx.app

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.logyx.core.data.SmartLoadsRepository
import pe.logyx.core.data.TripsRepository
import pe.logyx.core.model.*

/** Viajes y reservas en memoria (fase 4) con la máquina de estados mínima de las reservas. */
object TripData {
    val trip = TripView(
        id = "t1", carrierOrgId = "org-c", carrierName = "Transportes Sur Andino SRL", carrierTrustScore = 77.8,
        vehicleId = "v1", vehicleType = VehicleType.TRUCK_LARGE, vehiclePlate = "ASA-202",
        originCity = "LIMA", destinationCity = "JULIACA", departureAt = "2099-11-14T13:00:00Z",
        capacityKg = 12000.0, confirmedWeightKg = 0.0, remainingKg = 12000.0,
        pricingMode = TripPricingMode.PER_KG, rate = 0.48, notes = "Salida 6 a. m.", status = TripStatus.OPEN,
        pendingBookings = 1, confirmedBookings = 0,
    )

    fun booking(id: String = "b1", status: BookingStatus = BookingStatus.PENDING) = TripBookingView(
        id = id, tripId = "t1", status = status, requesterName = "Comercial Altiplano SAC", carrierName = "Transportes Sur Andino SRL",
        originCity = "LIMA", destinationCity = "JULIACA", departureAt = "2099-11-14T13:00:00Z", weightKg = 2500.0,
        cargoType = CargoType.GENERAL, cargoDescription = "Mercadería de bazar en cajas",
        pickupAddress = "Av. Argentina 1234, Callao", deliveryAddress = "Jr. San Román 456, Juliaca", price = 1200.0,
        createdAt = "2026-11-10T10:00:00Z",
    )
}

class FakeTrips(
    var trips: List<TripView> = listOf(TripData.trip),
    var bookings: List<TripBookingView> = emptyList(),
) : TripsRepository {
    val booked = mutableListOf<Pair<String, CreateTripBookingRequest>>()
    val confirmed = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    val created = mutableListOf<CreateTripRequest>()
    val rejected = mutableListOf<Pair<String, String?>>()

    private fun setBooking(id: String, status: BookingStatus): Result<TripBookingView> {
        bookings = bookings.map { if (it.id == id) it.copy(status = status) else it }
        return Result.success(bookings.first { it.id == id })
    }

    override suspend fun create(body: CreateTripRequest): Result<TripView> { created += body; return Result.success(trips.first()) }
    override suspend fun myTrips() = Result.success(trips)
    override suspend fun trip(id: String) = Result.success(trips.first { it.id == id })
    override suspend fun close(id: String) = Result.success(trips.first { it.id == id })
    override suspend fun cancel(id: String) = Result.success(trips.first { it.id == id })
    override suspend fun bookings(tripId: String) = Result.success(bookings.filter { it.tripId == tripId })
    override suspend fun confirm(bookingId: String): Result<TripBookingView> { confirmed += bookingId; return setBooking(bookingId, BookingStatus.CONFIRMED) }
    override suspend fun reject(bookingId: String, reason: String?): Result<TripBookingView> { rejected += bookingId to reason; return setBooking(bookingId, BookingStatus.REJECTED) }

    override suspend fun search(filter: TripFilter, page: Int, size: Int) = Result.success(Page(trips, 0, size, trips.size))
    override suspend fun book(tripId: String, body: CreateTripBookingRequest): Result<TripBookingView> {
        booked += tripId to body
        val b = TripData.booking("b-${booked.size}").copy(weightKg = body.weightKg, cargoDescription = body.cargoDescription, price = trips.first().priceFor(body.weightKg))
        bookings = bookings + b
        return Result.success(b)
    }
    override suspend fun myBookings(status: BookingStatus?) = Result.success(bookings)
    override suspend fun cancelBooking(bookingId: String): Result<TripBookingView> { cancelled += bookingId; return setBooking(bookingId, BookingStatus.CANCELLED) }
}

object SmartData {
    fun load(id: String = "s1", status: SmartLoadStatus = SmartLoadStatus.PENDING) = SmartLoadView(
        id, vehiclePlate = "ASA-202", capacityKg = 20000.0, totalWeightKg = 13500.0, combinedIncome = 14772.9, occupancyPct = 67.5,
        status = status, expiresAt = "2099-01-01T00:00:00Z",
        items = listOf(SmartLoadItemView("r1", "LIMA", "JULIACA", 7500.0, 8211.4), SmartLoadItemView("r2", "LIMA", "JULIACA", 6000.0, 6561.5)),
        route = listOf(SmartLoadRouteStop(StopType.PICKUP, "LIMA"), SmartLoadRouteStop(StopType.DELIVERY, "JULIACA")),
    )
}

class FakeSmartLoads(var items: List<SmartLoadView> = emptyList()) : SmartLoadsRepository {
    val accepted = mutableListOf<String>()
    val rejected = mutableListOf<String>()
    private fun decide(id: String, to: SmartLoadStatus): Result<SmartLoadView> {
        items = items.map { if (it.id == id) it.copy(status = to) else it }
        return Result.success(items.first { it.id == id })
    }
    override suspend fun returnLoads() = Result.success(emptyList<ReturnLoadView>())
    override suspend fun smartLoads(status: SmartLoadStatus?) = Result.success(items.filter { status == null || it.status == status })
    override suspend fun generate() = Result.success(emptyList<SmartLoadView>())
    override suspend fun accept(id: String): Result<SmartLoadView> { accepted += id; return decide(id, SmartLoadStatus.ACCEPTED) }
    override suspend fun reject(id: String): Result<SmartLoadView> { rejected += id; return decide(id, SmartLoadStatus.REJECTED) }
}

/** Sustituye los repositorios de viajes de Koin por fakes (se encadena después de [FakeKoinRule]). */
class FakeTripsKoinRule(private val trips: FakeTrips, private val smart: FakeSmartLoads = FakeSmartLoads()) : TestWatcher() {
    private var overrides: Module? = null
    override fun starting(description: Description) {
        overrides = module {
            single<TripsRepository> { trips }
            single<SmartLoadsRepository> { smart }
        }.also { loadKoinModules(it) }
    }
    override fun finished(description: Description) { overrides?.let { unloadKoinModules(it) } }
}
