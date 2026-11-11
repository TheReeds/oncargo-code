package pe.logyx.core.data

import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CreateTripBookingRequest
import pe.logyx.core.model.CreateTripRequest
import pe.logyx.core.model.Page
import pe.logyx.core.model.ReturnLoadView
import pe.logyx.core.model.SmartLoadStatus
import pe.logyx.core.model.SmartLoadView
import pe.logyx.core.model.TripBookingView
import pe.logyx.core.model.TripFilter
import pe.logyx.core.model.TripView
import pe.logyx.core.network.LogyxApi

/** Viajes publicados y reservas de espacio (fase 4): transportista publica y gestiona; PYME busca y reserva. */
interface TripsRepository {
    // Transportista
    suspend fun create(body: CreateTripRequest): Result<TripView>
    suspend fun myTrips(): Result<List<TripView>>
    suspend fun trip(id: String): Result<TripView>
    suspend fun close(id: String): Result<TripView>
    suspend fun cancel(id: String): Result<TripView>
    suspend fun bookings(tripId: String): Result<List<TripBookingView>>
    suspend fun confirm(bookingId: String): Result<TripBookingView>
    suspend fun reject(bookingId: String, reason: String? = null): Result<TripBookingView>

    // PYME
    suspend fun search(filter: TripFilter = TripFilter(), page: Int = 0, size: Int = 20): Result<Page<TripView>>
    suspend fun book(tripId: String, body: CreateTripBookingRequest): Result<TripBookingView>
    suspend fun myBookings(status: BookingStatus? = null): Result<List<TripBookingView>>
    suspend fun cancelBooking(bookingId: String): Result<TripBookingView>
}

/** Cargas de retorno (RF61-63) y sugerencias Smart Load (OE07) del transportista. */
interface SmartLoadsRepository {
    suspend fun returnLoads(): Result<List<ReturnLoadView>>
    suspend fun smartLoads(status: SmartLoadStatus? = null): Result<List<SmartLoadView>>
    /** Genera sugerencias ahora; devuelve solo las nuevas. */
    suspend fun generate(): Result<List<SmartLoadView>>
    suspend fun accept(id: String): Result<SmartLoadView>
    suspend fun reject(id: String): Result<SmartLoadView>
}

class DefaultTripsRepository(private val api: LogyxApi) : TripsRepository {
    override suspend fun create(body: CreateTripRequest) = apiCall { api.createTrip(body.copy(notes = body.notes?.takeIf { it.isNotBlank() })) }
    override suspend fun myTrips() = apiCall { api.myTrips() }
    override suspend fun trip(id: String) = apiCall { api.trip(id) }
    override suspend fun close(id: String) = apiCall { api.closeTrip(id) }
    override suspend fun cancel(id: String) = apiCall { api.cancelTrip(id) }
    override suspend fun bookings(tripId: String) = apiCall { api.tripBookings(tripId) }
    override suspend fun confirm(bookingId: String) = apiCall { api.confirmBooking(bookingId) }
    override suspend fun reject(bookingId: String, reason: String?) = apiCall { api.rejectBooking(bookingId, reason) }
    override suspend fun search(filter: TripFilter, page: Int, size: Int) = apiCall { api.searchTrips(filter, page, size) }
    override suspend fun book(tripId: String, body: CreateTripBookingRequest) = apiCall { api.bookTrip(tripId, body) }
    override suspend fun myBookings(status: BookingStatus?) = apiCall { api.myBookings(status) }
    override suspend fun cancelBooking(bookingId: String) = apiCall { api.cancelBooking(bookingId) }
}

class DefaultSmartLoadsRepository(private val api: LogyxApi) : SmartLoadsRepository {
    override suspend fun returnLoads() = apiCall { api.returnLoads() }
    override suspend fun smartLoads(status: SmartLoadStatus?) = apiCall { api.smartLoads(status) }
    override suspend fun generate() = apiCall { api.generateSmartLoads() }
    override suspend fun accept(id: String) = apiCall { api.acceptSmartLoad(id) }
    override suspend fun reject(id: String) = apiCall { api.rejectSmartLoad(id) }
}
