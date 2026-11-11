package pe.logyx.feature.trips

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.SmartLoadsRepository
import pe.logyx.core.data.TripsRepository
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ───────────── Datos y fakes ─────────────

private val lima = CityItem("LIMA", "Lima", -12.04, -77.04)
private val juliaca = CityItem("JULIACA", "Juliaca", -15.5, -70.1)
private val truck = VehicleView("v1", "ASA-202", VehicleType.TRUCK_LARGE, 20000.0, null, VehicleStatus.AVAILABLE)
private val van = VehicleView("v2", "ABC-101", VehicleType.VAN, 1500.0, null, VehicleStatus.AVAILABLE)

private fun aTrip(id: String = "t1", status: TripStatus = TripStatus.OPEN, remaining: Double = 12000.0, departure: String = "2099-11-14T13:00:00Z") = TripView(
    id = id, carrierName = "Sur Andino", vehicleId = "v1", vehicleType = VehicleType.TRUCK_LARGE, vehiclePlate = "ASA-202",
    originCity = "LIMA", destinationCity = "JULIACA", departureAt = departure, capacityKg = 12000.0, remainingKg = remaining,
    pricingMode = TripPricingMode.PER_KG, rate = 0.48, status = status,
)

private fun booking(id: String, status: BookingStatus = BookingStatus.PENDING, created: String = "2026-11-10T10:00:00Z") = TripBookingView(
    id = id, tripId = "t1", status = status, requesterName = "Comercial Altiplano SAC", weightKg = 2500.0, price = 1200.0, createdAt = created,
)

private val smartItem = SmartLoadItemView("r1", "LIMA", "JULIACA", 7500.0, 8211.4)

private fun smartLoad(id: String, status: SmartLoadStatus = SmartLoadStatus.PENDING) = SmartLoadView(
    id, vehiclePlate = "ASA-202", capacityKg = 20000.0, totalWeightKg = 13500.0, combinedIncome = 14772.9, occupancyPct = 67.5, status = status,
    items = listOf(smartItem, smartItem.copy(requestId = "r2")),
)

private fun returnLoad(alreadyBid: Boolean = false) = ReturnLoadView(
    requestId = "r9", vehicleId = "v1", vehiclePlate = "ASA-202", fromCity = "JULIACA", originCity = "JULIACA", destinationCity = "AREQUIPA",
    weightKg = 3500.0, pickupDate = "2026-11-11", suggestedPrice = 913.11, priceFloor = 684.83, matchScore = 96.0, alreadyBid = alreadyBid,
)

private class FakeTrips : TripsRepository {
    var created: CreateTripRequest? = null
    var createResult: Result<TripView> = Result.success(aTrip("new"))
    var myTrips: Result<List<TripView>> = Result.success(emptyList())
    var tripResult: Result<TripView> = Result.success(aTrip())
    var bookings: Result<List<TripBookingView>> = Result.success(emptyList())
    var actionResult: Result<TripBookingView> = Result.success(booking("b1", BookingStatus.CONFIRMED))
    val calls = mutableListOf<String>()
    var searchResult: Result<Page<TripView>> = Result.success(Page(emptyList(), 0, 20, 0))
    var lastFilter: TripFilter? = null
    var lastBooking: Pair<String, CreateTripBookingRequest>? = null
    var bookResult: Result<TripBookingView> = Result.success(booking("nb"))
    var mine: Result<List<TripBookingView>> = Result.success(emptyList())

    override suspend fun create(body: CreateTripRequest): Result<TripView> { created = body; return createResult }
    override suspend fun myTrips() = myTrips
    override suspend fun trip(id: String) = tripResult
    override suspend fun close(id: String): Result<TripView> { calls += "close:$id"; return tripResult }
    override suspend fun cancel(id: String): Result<TripView> { calls += "cancel:$id"; return tripResult }
    override suspend fun bookings(tripId: String) = bookings
    override suspend fun confirm(bookingId: String): Result<TripBookingView> { calls += "confirm:$bookingId"; return actionResult }
    override suspend fun reject(bookingId: String, reason: String?): Result<TripBookingView> { calls += "reject:$bookingId"; return actionResult }
    override suspend fun search(filter: TripFilter, page: Int, size: Int): Result<Page<TripView>> { lastFilter = filter; return searchResult }
    override suspend fun book(tripId: String, body: CreateTripBookingRequest): Result<TripBookingView> { lastBooking = tripId to body; return bookResult }
    override suspend fun myBookings(status: BookingStatus?) = mine
    override suspend fun cancelBooking(bookingId: String): Result<TripBookingView> { calls += "cancelBooking:$bookingId"; return actionResult }
}

private class FakeSmart : SmartLoadsRepository {
    var returns: Result<List<ReturnLoadView>> = Result.success(emptyList())
    var smarts: Result<List<SmartLoadView>> = Result.success(emptyList())
    var generated: Result<List<SmartLoadView>> = Result.success(emptyList())
    var decision: Result<SmartLoadView> = Result.success(smartLoad("s1", SmartLoadStatus.ACCEPTED))
    var lastStatus: SmartLoadStatus? = null
    val calls = mutableListOf<String>()
    override suspend fun returnLoads() = returns
    override suspend fun smartLoads(status: SmartLoadStatus?): Result<List<SmartLoadView>> { lastStatus = status; return smarts }
    override suspend fun generate() = generated
    override suspend fun accept(id: String): Result<SmartLoadView> { calls += "accept:$id"; return decision }
    override suspend fun reject(id: String): Result<SmartLoadView> { calls += "reject:$id"; return decision }
}

private class FakeLoads(var fleet: List<VehicleView> = listOf(truck)) : LoadsRepository {
    var placed: Triple<String, String, Double>? = null
    var placeResult: Result<BidView>? = null
    override suspend fun marketplace() = Result.success(emptyList<RequestView>())
    override suspend fun load(id: String) = Result.failure<RequestView>(IllegalStateException())
    override suspend fun myBids() = Result.success(emptyList<RequestView>())
    override suspend fun vehicles() = Result.success(fleet)
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?): Result<BidView> {
        placed = Triple(requestId, vehicleId, amount)
        return placeResult ?: Result.success(BidView("b1", requestId, amount = amount, status = BidStatus.PENDING))
    }
}

private class FakeCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(lima, juliaca))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private fun apiError(code: String, message: String, field: String? = null, status: Int = 409) = ApiException(status, code, message, field)

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = OffsetDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneOffset.ofHours(-5))
    private fun validForm() = PublishTripForm(
        origin = lima, destination = juliaca, date = LocalDate.of(2026, 11, 14), time = LocalTime.of(8, 0),
        vehicleId = "v1", capacityText = "12000", pricingMode = TripPricingMode.PER_KG, rateText = "0.48",
    )

    // ───────────── Formato ─────────────

    @Test fun `kg se formatea con miles y se parsea con comas`() {
        assertEquals("12,000 kg", kgText(12000.0))
        assertEquals("2.5 kg", kgText(2.5))
        assertEquals(2500.0, parseKg("2,500"))
        assertNull(parseKg("abc"))
        assertEquals("12.50", sanitizeKgInput("12a.5.0"))
    }

    // ───────────── Validación del formulario de viaje ─────────────

    @Test fun `formulario vacio marca todos los errores`() {
        val e = validateTrip(PublishTripForm(), listOf(truck), now)
        assertTrue(e.any)
        assertNotNull(e.origin); assertNotNull(e.destination); assertNotNull(e.departure)
        assertNotNull(e.vehicle); assertNotNull(e.capacity); assertNotNull(e.rate)
    }

    @Test fun `formulario valido no tiene errores`() {
        assertFalse(validateTrip(validForm(), listOf(truck), now).any)
    }

    @Test fun `salida en el pasado y destino igual al origen`() {
        val past = validateTrip(validForm().copy(date = LocalDate.of(2026, 11, 10), time = LocalTime.of(9, 0)), listOf(truck), now)
        assertEquals(UiText.of(R.string.trips_err_departure_past), past.departure)
        val same = validateTrip(validForm().copy(destination = lima), listOf(truck), now)
        assertEquals(UiText.of(R.string.trips_err_same_city), same.destination)
    }

    @Test fun `RN12 la capacidad no puede superar la del vehiculo`() {
        val e = validateTrip(validForm().copy(vehicleId = "v2", capacityText = "5000"), listOf(truck, van), now)
        assertEquals(UiText.of(R.string.trips_err_capacity_vehicle, "ABC-101", "1,500 kg"), e.capacity)
        assertEquals(UiText.of(R.string.trips_err_capacity_max, "40,000 kg"), validateTrip(validForm().copy(capacityText = "50000"), listOf(truck), now).capacity)
    }

    // ───────────── Publicar viaje ─────────────

    @Test fun `publicar carga flota y ciudades y preselecciona el unico vehiculo`() = runTest {
        val vm = PublishTripViewModel(FakeTrips(), FakeLoads(), FakeCatalog())
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(2, s.cities.size)
        assertEquals("v1", s.form.vehicleId)
    }

    @Test fun `publicar invalido no llama al API`() = runTest {
        val trips = FakeTrips()
        val vm = PublishTripViewModel(trips, FakeLoads(), FakeCatalog())
        vm.submit(now)
        assertNull(trips.created)
        assertNotNull(vm.state.value.errors.origin)
        vm.onOrigin(lima)
        assertNull(vm.state.value.errors.origin)
    }

    @Test fun `publicar valido envia hora de Lima y expone el id`() = runTest {
        val trips = FakeTrips()
        val vm = PublishTripViewModel(trips, FakeLoads(), FakeCatalog())
        vm.onOrigin(lima); vm.onDestination(juliaca)
        vm.onDate(LocalDate.of(2026, 11, 14)); vm.onTime(LocalTime.of(8, 0))
        vm.onCapacity("12000"); vm.onRate("0.48"); vm.onNotes("  Salida 6 a. m.  ")
        vm.submit(now)
        val body = trips.created!!
        assertEquals("2026-11-14T08:00:00-05:00", body.departureAt)
        assertEquals("LIMA", body.originCity)
        assertEquals(12000.0, body.capacityKg)
        assertEquals(0.48, body.rate)
        assertEquals("Salida 6 a. m.", body.notes)
        assertEquals("new", vm.state.value.publishedId)
    }

    @Test fun `error del servidor sobre capacityKg va inline`() = runTest {
        val trips = FakeTrips().apply { createResult = Result.failure(apiError("LOGYX-TRP-001", "La capacidad supera la de tu vehículo", "capacityKg", 400)) }
        val vm = PublishTripViewModel(trips, FakeLoads(), FakeCatalog())
        vm.onOrigin(lima); vm.onDestination(juliaca); vm.onDate(LocalDate.of(2026, 11, 14)); vm.onTime(LocalTime.of(8, 0))
        vm.onCapacity("12000"); vm.onRate("0.48")
        vm.submit(now)
        assertEquals(UiText.Raw("La capacidad supera la de tu vehículo"), vm.state.value.errors.capacity)
        assertNull(vm.state.value.publishedId)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun `error generico al publicar va al snackbar`() = runTest {
        val trips = FakeTrips().apply { createResult = Result.failure(apiError("LOGYX-GEN-500", "No pudimos publicar", status = 500)) }
        val vm = PublishTripViewModel(trips, FakeLoads(), FakeCatalog())
        vm.onOrigin(lima); vm.onDestination(juliaca); vm.onDate(LocalDate.of(2026, 11, 14)); vm.onTime(LocalTime.of(8, 0))
        vm.onCapacity("1000"); vm.onRate("1")
        vm.submit(now)
        assertEquals("No pudimos publicar", vm.state.value.message?.text)
    }

    // ───────────── Mis viajes ─────────────

    @Test fun `mis viajes - gestionables primero por salida`() = runTest {
        val trips = FakeTrips().apply {
            myTrips = Result.success(listOf(
                aTrip("done", TripStatus.COMPLETED, departure = "2026-01-01T00:00:00Z"),
                aTrip("late", TripStatus.OPEN, departure = "2099-12-01T00:00:00Z"),
                aTrip("soon", TripStatus.FILLING, departure = "2099-11-01T00:00:00Z"),
            ))
        }
        val vm = CarrierTripsViewModel(trips, FakeRealtime())
        assertEquals(listOf("soon", "late", "done"), vm.state.value.trips.map { it.id })
    }

    @Test fun `mis viajes - error de red muestra mensaje`() = runTest {
        val vm = CarrierTripsViewModel(FakeTrips().apply { myTrips = Result.failure(apiError("NETWORK", "No pudimos conectar con LOGYX.", status = 0)) }, FakeRealtime())
        assertEquals("No pudimos conectar con LOGYX.", vm.state.value.error)
    }

    // ───────────── Detalle y reservas del transportista ─────────────

    @Test fun `detalle recien publicado muestra mensaje y ordena reservas pendientes primero`() = runTest {
        val trips = FakeTrips().apply {
            bookings = Result.success(listOf(booking("old", BookingStatus.CONFIRMED), booking("new", BookingStatus.PENDING)))
        }
        val vm = CarrierTripDetailViewModel("t1", true, trips, FakeRealtime())
        assertEquals(R.string.trips_published, vm.state.value.message?.textRes)
        assertEquals(listOf("new", "old"), vm.state.value.bookings.map { it.id })
    }

    @Test fun `confirmar reserva pide confirmacion y luego llama al API`() = runTest {
        val trips = FakeTrips().apply { bookings = Result.success(listOf(booking("b1"))) }
        val vm = CarrierTripDetailViewModel("t1", false, trips, FakeRealtime())
        vm.askConfirm(booking("b1"))
        assertTrue(vm.state.value.pending is TripAction.Confirm)
        assertTrue(trips.calls.isEmpty())
        vm.perform()
        assertEquals(listOf("confirm:b1"), trips.calls)
        assertNull(vm.state.value.pending)
        assertEquals(R.string.trips_booking_confirmed, vm.state.value.message?.textRes)
    }

    @Test fun `rechazar y cancelar viaje usan los endpoints correctos`() = runTest {
        val trips = FakeTrips()
        val vm = CarrierTripDetailViewModel("t1", false, trips, FakeRealtime())
        vm.askReject(booking("b2")); vm.perform()
        vm.askClose(); vm.perform()
        vm.askCancel(); vm.perform()
        assertEquals(listOf("reject:b2", "close:t1", "cancel:t1"), trips.calls)
    }

    @Test fun `descartar el dialogo no ejecuta nada y un error de API se muestra`() = runTest {
        val trips = FakeTrips().apply { actionResult = Result.failure(apiError("LOGYX-TRP-004", "El peso excede la capacidad restante", "weightKg")) }
        val vm = CarrierTripDetailViewModel("t1", false, trips, FakeRealtime())
        vm.askConfirm(booking("b1")); vm.dismissConfirm()
        assertNull(vm.state.value.pending)
        assertTrue(trips.calls.isEmpty())
        vm.askConfirm(booking("b1")); vm.perform()
        assertEquals("El peso excede la capacidad restante", vm.state.value.message?.text)
    }

    // ───────────── Cargas de retorno ─────────────

    @Test fun `retorno - ordena por puntaje y prellena el precio sugerido`() = runTest {
        val smart = FakeSmart().apply { returns = Result.success(listOf(returnLoad().copy(requestId = "low", matchScore = 50.0), returnLoad())) }
        val vm = ReturnLoadsViewModel(smart, FakeLoads())
        assertEquals(listOf("r9", "low"), vm.state.value.items.map { it.requestId })
        vm.openBid(vm.state.value.items.first())
        assertEquals("913.11", vm.state.value.bid?.amountText)
    }

    @Test fun `retorno - oferta bajo el piso da error y no pide confirmacion`() = runTest {
        val vm = ReturnLoadsViewModel(FakeSmart().apply { returns = Result.success(listOf(returnLoad())) }, FakeLoads())
        vm.openBid(returnLoad())
        vm.onAmount("500")
        vm.requestConfirm()
        assertEquals(UiText.of(R.string.trips_bid_err_floor, "S/ 684.83"), vm.state.value.bid?.amountError)
        assertFalse(vm.state.value.bid!!.confirming)
    }

    @Test fun `retorno - ofertar usa el vehiculo sugerido`() = runTest {
        val loads = FakeLoads()
        val vm = ReturnLoadsViewModel(FakeSmart().apply { returns = Result.success(listOf(returnLoad())) }, loads)
        vm.openBid(returnLoad())
        vm.onAmount("900")
        vm.requestConfirm()
        assertTrue(vm.state.value.bid!!.confirming)
        vm.submit()
        assertEquals(Triple("r9", "v1", 900.0), loads.placed)
        assertNull(vm.state.value.bid)
        assertEquals(R.string.trips_bid_sent, vm.state.value.message?.textRes)
    }

    @Test fun `retorno - ya ofertada no abre la hoja`() = runTest {
        val vm = ReturnLoadsViewModel(FakeSmart(), FakeLoads())
        vm.openBid(returnLoad(alreadyBid = true))
        assertNull(vm.state.value.bid)
    }

    @Test fun `retorno - error de monto del API queda en el campo`() = runTest {
        val loads = FakeLoads().apply { placeResult = Result.failure(apiError("LOGYX-BID-002", "Tu oferta es menor al piso", "amount")) }
        val vm = ReturnLoadsViewModel(FakeSmart(), loads)
        vm.openBid(returnLoad()); vm.requestConfirm(); vm.submit()
        assertEquals(UiText.Raw("Tu oferta es menor al piso"), vm.state.value.bid?.amountError)
        assertFalse(vm.state.value.bid!!.submitting)
    }

    // ───────────── Smart Load ─────────────

    @Test fun `smart load - inicia en pendientes y recarga al cambiar de filtro`() = runTest {
        val smart = FakeSmart().apply { smarts = Result.success(listOf(smartLoad("s1"))) }
        val vm = SmartLoadsViewModel(smart, FakeRealtime())
        assertEquals(SmartLoadStatus.PENDING, smart.lastStatus)
        vm.setFilter(SmartLoadStatus.ACCEPTED)
        assertEquals(SmartLoadStatus.ACCEPTED, smart.lastStatus)
        assertEquals(SmartLoadStatus.ACCEPTED, vm.state.value.filter)
    }

    @Test fun `smart load - generar informa cuantas sugerencias nuevas hay`() = runTest {
        val smart = FakeSmart()
        val vm = SmartLoadsViewModel(smart, FakeRealtime())
        vm.generate()
        assertEquals(R.string.trips_smart_generated_none, vm.state.value.message?.textRes)
        smart.generated = Result.success(listOf(smartLoad("a"), smartLoad("b")))
        vm.generate()
        assertEquals(R.string.trips_smart_generated_many, vm.state.value.message?.textRes)
        assertEquals(listOf<Any>(2), vm.state.value.message?.args)
    }

    @Test fun `smart load - aceptar y rechazar piden confirmacion`() = runTest {
        val smart = FakeSmart().apply { smarts = Result.success(listOf(smartLoad("s1"))) }
        val vm = SmartLoadsViewModel(smart, FakeRealtime())
        vm.askAccept(smartLoad("s1"))
        assertTrue(vm.state.value.pending!!.accept)
        assertTrue(smart.calls.isEmpty())
        vm.confirm()
        assertEquals(listOf("accept:s1"), smart.calls)
        assertEquals(R.string.trips_smart_accepted, vm.state.value.message?.textRes)
        vm.askReject(smartLoad("s1")); vm.confirm()
        assertEquals(listOf("accept:s1", "reject:s1"), smart.calls)
    }

    @Test fun `smart load - sugerencia vencida muestra el mensaje del API`() = runTest {
        val smart = FakeSmart().apply { decision = Result.failure(apiError("LOGYX-TRP-009", "La sugerencia venció")) }
        val vm = SmartLoadsViewModel(smart, FakeRealtime())
        vm.askAccept(smartLoad("s1")); vm.confirm()
        assertEquals("La sugerencia venció", vm.state.value.message?.text)
        assertNull(vm.state.value.pending)
    }

    // ───────────── PYME: búsqueda y reserva ─────────────

    @Test fun `busqueda - aplicar filtros los envia al servidor`() = runTest {
        val trips = FakeTrips()
        val vm = TripSearchViewModel(trips, FakeCatalog())
        vm.applyFilters(lima, juliaca, LocalDate.of(2026, 11, 14), null, "2,500")
        assertEquals(TripFilter("LIMA", "JULIACA", "2026-11-14", "2026-11-14", 2500.0), trips.lastFilter)
        assertEquals(4, vm.state.value.filter.activeCount)
        vm.clearFilters()
        assertEquals(TripFilter(), trips.lastFilter)
    }

    @Test fun `busqueda - pagina y detecta mas resultados`() = runTest {
        val trips = FakeTrips().apply { searchResult = Result.success(Page(listOf(aTrip("a")), 0, 1, 3)) }
        val vm = TripSearchViewModel(trips, FakeCatalog())
        assertTrue(vm.state.value.hasMore)
        trips.searchResult = Result.success(Page(listOf(aTrip("b")), 1, 1, 3))
        vm.loadMore()
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.id })
    }

    @Test fun `reserva - validacion inline sin llamar al API`() = runTest {
        val trips = FakeTrips()
        val vm = TripSearchViewModel(trips, FakeCatalog())
        vm.openBook(aTrip(remaining = 1000.0))
        vm.requestConfirm()
        val f = vm.state.value.book!!
        assertNotNull(f.weightError); assertNotNull(f.descriptionError); assertNotNull(f.cargoTypeError)
        assertNotNull(f.pickupError); assertNotNull(f.deliveryError)
        assertFalse(f.confirming)
        vm.onWeight("5000")
        vm.requestConfirm()
        assertEquals(UiText.of(R.string.trips_book_err_weight_max, "1,000 kg"), vm.state.value.book!!.weightError)
        assertNull(trips.lastBooking)
    }

    private fun fillBooking(vm: TripSearchViewModel, weight: String = "2500") {
        vm.openBook(aTrip())
        vm.onWeight(weight); vm.onDescription("Mercadería de bazar en cajas"); vm.onCargoType(CargoType.GENERAL)
        vm.onPickup("Av. Argentina 1234, Callao"); vm.onDelivery("Jr. San Román 456, Juliaca")
    }

    @Test fun `reserva - precio estimado, confirmacion y envio`() = runTest {
        val trips = FakeTrips()
        val vm = TripSearchViewModel(trips, FakeCatalog())
        fillBooking(vm)
        assertEquals(1200.0, vm.state.value.book!!.estimatedPrice)
        vm.requestConfirm()
        assertTrue(vm.state.value.book!!.confirming)
        assertNull(trips.lastBooking)
        vm.submit()
        val (id, body) = trips.lastBooking!!
        assertEquals("t1", id)
        assertEquals(2500.0, body.weightKg)
        assertEquals(CargoType.GENERAL, body.cargoType)
        assertNull(vm.state.value.book)
        assertEquals(1, vm.state.value.bookedTick)
        assertEquals(R.string.trips_book_sent, vm.state.value.message?.textRes)
    }

    @Test fun `reserva - TRP-004 se muestra en el campo peso y no cierra la hoja`() = runTest {
        val trips = FakeTrips().apply { bookResult = Result.failure(apiError("LOGYX-TRP-004", "Solo quedan 1,000 kg", "weightKg")) }
        val vm = TripSearchViewModel(trips, FakeCatalog())
        fillBooking(vm)
        vm.requestConfirm(); vm.submit()
        val f = vm.state.value.book!!
        assertEquals(UiText.Raw("Solo quedan 1,000 kg"), f.weightError)
        assertFalse(f.submitting); assertFalse(f.confirming)
        assertEquals(0, vm.state.value.bookedTick)
    }

    @Test fun `reserva - error generico va al snackbar`() = runTest {
        val trips = FakeTrips().apply { bookResult = Result.failure(apiError("LOGYX-TRP-003", "El viaje ya no admite reservas")) }
        val vm = TripSearchViewModel(trips, FakeCatalog())
        fillBooking(vm)
        vm.requestConfirm(); vm.submit()
        assertEquals("El viaje ya no admite reservas", vm.state.value.message?.text)
    }

    // ───────────── PYME: mis reservas ─────────────

    @Test fun `mis reservas - vivas primero y cuenta activas`() = runTest {
        val trips = FakeTrips().apply {
            mine = Result.success(listOf(
                booking("rej", BookingStatus.REJECTED, "2026-11-10T12:00:00Z"),
                booking("old", BookingStatus.CONFIRMED, "2026-11-09T12:00:00Z"),
                booking("new", BookingStatus.PENDING, "2026-11-10T09:00:00Z"),
            ))
        }
        val vm = MyBookingsViewModel(trips, FakeRealtime())
        assertEquals(listOf("new", "old", "rej"), vm.state.value.bookings.map { it.id })
        assertEquals(2, vm.state.value.activeCount)
    }

    @Test fun `mis reservas - cancelar exige confirmacion`() = runTest {
        val trips = FakeTrips().apply { mine = Result.success(listOf(booking("b1"))) }
        val vm = MyBookingsViewModel(trips, FakeRealtime())
        vm.askCancel(booking("b1"))
        assertNotNull(vm.state.value.pendingCancel)
        assertTrue(trips.calls.isEmpty())
        vm.confirmCancel()
        assertEquals(listOf("cancelBooking:b1"), trips.calls)
        assertEquals(R.string.trips_booking_cancelled, vm.state.value.message?.textRes)
        assertNull(vm.state.value.pendingCancel)
    }

    @Test fun `mis reservas - SHP-003 al cancelar una en ruta muestra el mensaje`() = runTest {
        val trips = FakeTrips().apply {
            mine = Result.success(listOf(booking("b1", BookingStatus.CONFIRMED)))
            actionResult = Result.failure(apiError("LOGYX-SHP-003", "El envío ya salió del recojo"))
        }
        val vm = MyBookingsViewModel(trips, FakeRealtime())
        vm.askCancel(booking("b1", BookingStatus.CONFIRMED)); vm.confirmCancel()
        assertEquals("El envío ya salió del recojo", vm.state.value.message?.text)
    }
}
