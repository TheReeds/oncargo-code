package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CreateTripBookingRequest
import pe.logyx.core.model.CreateTripRequest
import pe.logyx.core.model.DashboardSummary
import pe.logyx.core.model.NotificationTarget
import pe.logyx.core.model.NotificationType
import pe.logyx.core.model.NotificationView
import pe.logyx.core.model.SmartLoadStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.TripFilter
import pe.logyx.core.model.TripPricingMode
import pe.logyx.core.model.TripStatus
import pe.logyx.core.model.TripView
import pe.logyx.core.model.isBookable
import pe.logyx.core.model.priceFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ejemplos de docs/api/CHANGELOG.md (fase 4) contra los DTOs y las llamadas de `LogyxApi`. */
class Phase4ParsingTest {
    private val ts = """"meta":{"timestamp":"2026-11-10T20:00:00Z"}"""
    private val u = "11111111-1111-1111-1111-111111111111"

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String, capture: suspend (HttpRequestData) -> Unit = {}): LogyxApi {
        val engine = MockEngine { req ->
            capture(req)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
    }

    private val tripJson = """{"id":"$u","carrierOrgId":"$u","carrierName":"Transportes Sur Andino SRL","carrierTrustScore":77.8,"vehicleId":"$u","vehicleType":"TRUCK_LARGE","vehiclePlate":"ASA-202","originCity":"LIMA","destinationCity":"JULIACA","departureAt":"2026-11-14T13:00:00Z","capacityKg":12000,"confirmedWeightKg":0,"remainingKg":12000,"pricingMode":"PER_KG","rate":0.48,"notes":"Salida 6 a. m.","status":"OPEN","createdAt":"x","pendingBookings":0,"confirmedBookings":0}"""

    @Test fun `publicar viaje envia el cuerpo y parsea TripView`() = runTest {
        var body = ""
        var path = ""
        val t = api(HttpStatusCode.Created, """{"data":$tripJson,$ts}""") { req -> path = req.url.encodedPath; body = req.body.toByteArray().decodeToString() }
            .createTrip(CreateTripRequest("LIMA", "JULIACA", "2026-11-14T08:00:00-05:00", u, 12000.0, TripPricingMode.PER_KG, 0.48, "Salida 6 a. m."))
        assertEquals("/api/v1/trips", path)
        assertTrue(body.contains(""""departureAt":"2026-11-14T08:00:00-05:00""""))
        assertTrue(body.contains(""""pricingMode":"PER_KG""""))
        assertEquals(TripStatus.OPEN, t.status)
        assertEquals(77.8, t.carrierTrustScore)
        assertEquals(12000.0, t.remainingKg)
        assertTrue(t.isBookable)
    }

    @Test fun `RN12 llega como TRP-001 con field capacityKg`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.BadRequest, """{"error":{"code":"LOGYX-TRP-001","field":"capacityKg","message":"La capacidad supera la de tu vehículo"},$ts}""")
                .createTrip(CreateTripRequest("LIMA", "JULIACA", "2026-11-14T08:00:00-05:00", u, 99000.0, TripPricingMode.PER_KG, 0.5))
        }
        assertEquals("LOGYX-TRP-001", e.code)
        assertEquals("capacityKg", e.field)
    }

    @Test fun `viaje para la PYME no trae vehicleId ni contadores`() {
        val t = LogyxJson.decodeFromString<TripView>("""{"id":"$u","carrierName":"X","vehicleType":"TRUCK_LARGE","vehiclePlate":"ASA-202","originCity":"LIMA","destinationCity":"JULIACA","departureAt":"x","capacityKg":12000,"remainingKg":7500,"pricingMode":"PER_TRIP","rate":5500,"status":"FILLING","vehicleId":null,"pendingBookings":null,"confirmedBookings":null,"campoNuevo":1}""")
        assertNull(t.vehicleId)
        assertNull(t.pendingBookings)
        assertEquals(TripStatus.FILLING, t.status)
    }

    @Test fun `busqueda de viajes envia filtros y pagina`() = runTest {
        var query = ""
        val page = api(body = """{"data":[$tripJson],"meta":{"timestamp":"x","page":0,"size":20,"total":35}}""") { query = it.url.encodedQuery }
            .searchTrips(TripFilter("LIMA", "JULIACA", "2026-11-14", "2026-11-20", 2500.0), page = 0, size = 20)
        assertEquals(35, page.total)
        assertTrue(page.hasMore)
        listOf("originCity=LIMA", "destinationCity=JULIACA", "fromDate=2026-11-14", "toDate=2026-11-20", "minWeightKg=2500.0", "page=0", "size=20").forEach {
            assertTrue(query.contains(it), "falta $it en $query")
        }
    }

    @Test fun `reservar y parsear TripBookingView`() = runTest {
        var body = ""
        val b = api(HttpStatusCode.Created, """{"data":{"id":"$u","tripId":"$u","status":"PENDING","requesterOrgId":"$u","requesterName":"Comercial Altiplano SAC","carrierOrgId":"$u","carrierName":"Transportes Sur Andino SRL","originCity":"LIMA","destinationCity":"JULIACA","departureAt":"x","weightKg":2500,"cargoType":"GENERAL","cargoDescription":"Bazar","pickupAddress":"A","deliveryAddress":"B","price":1200.00,"decisionNote":null,"requestId":null,"shipmentId":null,"createdAt":"x","decidedAt":null},$ts}""") { body = it.body.toByteArray().decodeToString() }
            .bookTrip(u, CreateTripBookingRequest(2500.0, "Bazar", CargoType.GENERAL, "A", "B"))
        assertEquals(BookingStatus.PENDING, b.status)
        assertEquals(1200.0, b.price)
        assertNull(b.shipmentId)
        assertTrue(body.contains(""""weightKg":2500.0"""))
        assertTrue(body.contains(""""cargoType":"GENERAL""""))
    }

    @Test fun `TRP-004 peso excede la capacidad restante`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.Conflict, """{"error":{"code":"LOGYX-TRP-004","field":"weightKg","message":"Solo quedan 1,000 kg"},$ts}""")
                .bookTrip(u, CreateTripBookingRequest(5000.0, "x", CargoType.GENERAL, "A", "B"))
        }
        assertEquals("weightKg", e.field)
        assertEquals(409, e.status)
    }

    @Test fun `confirmar rechazar y cancelar reservas`() = runTest {
        val seen = mutableListOf<Pair<HttpMethod, String>>()
        var rejectBody = ""
        val confirmed = """{"data":{"id":"$u","tripId":"$u","status":"CONFIRMED","weightKg":2500,"requestId":"$u","shipmentId":"$u"},$ts}"""
        val a = api(body = confirmed) { seen += it.method to it.url.encodedPath }
        assertEquals(u, a.confirmBooking(u).shipmentId)
        api(body = """{"data":{"id":"$u","tripId":"$u","status":"REJECTED","weightKg":1,"decisionNote":"Sin cupo"},$ts}""") {
            seen += it.method to it.url.encodedPath; rejectBody = it.body.toByteArray().decodeToString()
        }.rejectBooking(u, "Sin cupo").also { assertEquals(BookingStatus.REJECTED, it.status) }
        api(body = confirmed.replace("CONFIRMED", "CANCELLED")) { seen += it.method to it.url.encodedPath }.cancelBooking(u)
        assertEquals(
            listOf(HttpMethod.Post to "/api/v1/trip-bookings/$u/confirm", HttpMethod.Post to "/api/v1/trip-bookings/$u/reject", HttpMethod.Post to "/api/v1/trip-bookings/$u/cancel"),
            seen,
        )
        assertTrue(rejectBody.contains(""""reason":"Sin cupo""""))
    }

    @Test fun `mis reservas con filtro de estado`() = runTest {
        var query: String? = null
        api(body = """{"data":[],$ts}""") { query = it.url.encodedQuery }.myBookings(BookingStatus.PENDING)
        assertEquals("status=PENDING", query)
    }

    @Test fun `cargas de retorno`() = runTest {
        val r = api(body = """{"data":[{"requestId":"$u","requesterOrgId":"$u","requesterName":"Comercial Altiplano SAC","shipmentId":"$u","vehicleId":"$u","vehiclePlate":"ASA-202","fromCity":"JULIACA","shipmentDelivered":true,"originCity":"JULIACA","originAddress":"x","destinationCity":"AREQUIPA","destinationAddress":"y","cargoType":"GENERAL","cargoDescription":"Lana de alpaca en fardos","weightKg":3500,"pickupDate":"2026-11-11","suggestedPrice":913.11,"priceFloor":684.83,"distanceKm":0.0,"waitDays":1,"matchScore":96,"alreadyBid":false}],$ts}""").returnLoads()
        val l = r.single()
        assertEquals(913.11, l.suggestedPrice)
        assertEquals(684.83, l.priceFloor)
        assertEquals(96.0, l.matchScore)
        assertFalse(l.alreadyBid)
        assertEquals(u, l.vehicleId)
    }

    @Test fun `smart load con ruta e items`() = runTest {
        var query: String? = null
        val s = api(body = """{"data":[{"id":"$u","vehicleId":"$u","vehicleType":"TRUCK_LARGE","vehiclePlate":"ASA-202","capacityKg":20000,"totalWeightKg":13500,"combinedIncome":14772.90,"occupancyPct":67.50,"status":"PENDING","createdAt":"x","expiresAt":"x","decidedAt":null,"items":[{"requestId":"$u","originCity":"LIMA","destinationCity":"JULIACA","weightKg":7500,"income":8211.40,"pickupDate":"2026-11-13","pickupOrder":0,"deliveryOrder":0}],"route":[{"type":"PICKUP","city":"LIMA","requestId":"$u"},{"type":"DELIVERY","city":"JULIACA","requestId":"$u"}]}],$ts}""") { query = it.url.encodedQuery }
            .smartLoads(SmartLoadStatus.PENDING)
        assertEquals("status=PENDING", query)
        val l = s.single()
        assertEquals(67.5, l.occupancyPct)
        assertEquals(StopType.PICKUP, l.route.first().type)
        assertEquals(8211.40, l.items.single().income)
    }

    @Test fun `generar sugerencias POST y aceptar rechazar`() = runTest {
        val seen = mutableListOf<String>()
        api(HttpStatusCode.Created, """{"data":[],$ts}""") { seen += it.method.value + " " + it.url.encodedPath }.generateSmartLoads().also { assertTrue(it.isEmpty()) }
        val one = """{"data":{"id":"$u","status":"ACCEPTED","items":[]},$ts}"""
        assertEquals(SmartLoadStatus.ACCEPTED, api(body = one) { seen += it.method.value + " " + it.url.encodedPath }.acceptSmartLoad(u).status)
        api(body = one.replace("ACCEPTED", "REJECTED")) { seen += it.method.value + " " + it.url.encodedPath }.rejectSmartLoad(u)
        assertEquals(listOf("POST /api/v1/smart-loads/generate", "POST /api/v1/smart-loads/$u/accept", "POST /api/v1/smart-loads/$u/reject"), seen)
    }

    @Test fun `TRP-009 sugerencia vencida`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.Conflict, """{"error":{"code":"LOGYX-TRP-009","message":"La sugerencia venció"},$ts}""").acceptSmartLoad(u)
        }
        assertEquals("LOGYX-TRP-009", e.code)
    }

    @Test fun `dashboard con campos de fase 4`() = runTest {
        val d = api(body = """{"data":{"role":"CARRIER","compatibleLoads":4,"openTrips":2,"pendingTripBookings":1,"pendingSmartLoads":1,"returnLoads":3,"topReturnLoads":[{"requestId":"$u","originCity":"JULIACA","destinationCity":"AREQUIPA","weightKg":3500,"pickupDate":"2026-11-11","suggestedPrice":913.11,"distanceKm":0.0,"matchScore":96}]},$ts}""").dashboardSummary()
        assertEquals(2, d.openTrips)
        assertEquals(3, d.returnLoads)
        assertEquals("AREQUIPA", d.topReturnLoads.single().destinationCity)
        val pyme = LogyxJson.decodeFromString<DashboardSummary>("""{"role":"PYME","activeTripBookings":2}""")
        assertEquals(2, pyme.activeTripBookings)
        assertTrue(pyme.topReturnLoads.isEmpty())
    }

    @Test fun `enums de notificacion desconocidos no rompen`() {
        val n = LogyxJson.decodeFromString<NotificationView>("""{"id":"$u","type":"ALGO_FUTURO","title":"t","body":"b","relatedType":"OTRO_DESTINO","relatedId":"$u","read":false}""")
        assertEquals(NotificationType.UNKNOWN, n.type)
        assertNull(n.relatedType)
        val trip = LogyxJson.decodeFromString<NotificationView>("""{"id":"$u","type":"TRIP_BOOKING_REQUESTED","title":"t","body":"b","relatedType":"TRIP","relatedId":"$u","read":false}""")
        assertEquals(NotificationType.TRIP_BOOKING_REQUESTED, trip.type)
        assertEquals(NotificationTarget.TRIP, trip.relatedType)
        assertEquals(NotificationTarget.SMART_LOAD, LogyxJson.decodeFromString<NotificationView>("""{"id":"$u","type":"SMART_LOAD_SUGGESTED","title":"t","body":"b","relatedType":"SMART_LOAD"}""").relatedType)
    }

    private fun trip(mode: TripPricingMode, rate: Double, capacity: Double = 12000.0) = TripView(
        id = u, originCity = "LIMA", destinationCity = "JULIACA", departureAt = "x", capacityKg = capacity, remainingKg = capacity,
        pricingMode = mode, rate = rate, status = TripStatus.OPEN,
    )

    @Test fun `precio estimado de reserva espeja TripPricingPolicy`() {
        assertEquals(1200.0, trip(TripPricingMode.PER_KG, 0.48).priceFor(2500.0))
        assertEquals(1145.83, trip(TripPricingMode.PER_TRIP, 5500.0).priceFor(2500.0))
        assertEquals(1.0, trip(TripPricingMode.PER_KG, 0.01).priceFor(1.0)) // mínimo S/ 1.00
    }

    @Test fun `viaje cerrado no admite reservas`() {
        assertFalse(trip(TripPricingMode.PER_KG, 1.0).copy(status = TripStatus.CLOSED).isBookable)
        assertFalse(trip(TripPricingMode.PER_KG, 1.0).copy(remainingKg = 0.0).isBookable)
    }
}
