package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import pe.logyx.core.model.AcceptResult
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.AuthResponse
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.Role
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.VehicleType
import pe.logyx.core.model.VehicleView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ejemplos con la forma exacta de los esquemas de docs/api/openapi.yaml. */
class DtoParsingTest {
    private val ts = """"meta":{"timestamp":"2026-11-10T20:00:00Z"}"""
    private val u1 = "11111111-1111-1111-1111-111111111111"
    private val u2 = "22222222-2222-2222-2222-222222222222"

    private val userJson = """{"id":"$u1","email":"carrier@logyx.pe","fullName":"Carla Transportes","role":"ADMIN",
        "organizationId":"$u2","organizationName":"Transportes Andes SAC","organizationType":"CARRIER","rucVerified":true}"""

    private val requestJson = """{"id":"$u1","requesterOrgId":"$u2","requesterName":"Importadora Sur","originCity":"LIMA",
        "originAddress":"Av. Argentina 123","destinationCity":"JULIACA","destinationAddress":"Jr. Lima 45","cargoType":"GENERAL",
        "cargoDescription":"Abarrotes","weightKg":3200.0,"volumeM3":null,"pickupDate":"2026-11-28","distanceKm":1010.5,
        "suggestedPrice":1850.0,"priceFloor":1500.0,"status":"PUBLISHED","createdAt":"2026-11-10T12:15:50-05:00",
        "auction":{"id":"$u2","status":"OPEN","closesAt":"2026-11-10T13:15:50-05:00","maxBids":5,"bidCount":2,"closeReason":null},
        "myBid":{"id":"$u1","requestId":"$u1","carrierOrgId":"$u2","carrierName":"Transportes Andes","carrierRucVerified":true,
        "vehicleId":"$u2","amount":1700.5,"notes":"Entrega rápida","status":"PENDING","createdAt":"2026-11-10T12:20:00-05:00"}}"""

    private val shipmentJson = """{"id":"$u1","requestId":"$u2","requesterName":"Importadora Sur","carrierName":"Transportes Andes",
        "originCity":"LIMA","destinationCity":"JULIACA","cargoDescription":"Abarrotes","weightKg":3200.0,"agreedPrice":1700.5,
        "vehiclePlate":"ABC-123","driverId":"$u2","driverName":"Juan Quispe","status":"IN_TRANSIT","createdAt":"2026-11-10T12:15:50-05:00",
        "deliveredAt":null,"futureField":42,
        "stops":[{"id":"$u1","sequence":1,"type":"PICKUP","city":"LIMA","address":"Av. Argentina 123","status":"COMPLETED",
        "arrivedAt":"2026-11-10T14:00:00-05:00","completedAt":"2026-11-10T14:30:00-05:00"},
        {"id":"$u2","sequence":2,"type":"DELIVERY","city":"JULIACA","address":"Jr. Lima 45","status":"PENDING"}]}"""

    @Test fun `login response`() {
        val json = """{"data":{"accessToken":"jwt.token.here","expiresIn":3600,"user":$userJson},$ts}"""
        val env = LogyxJson.decodeFromString<ApiEnvelope<AuthResponse>>(json)
        assertEquals("jwt.token.here", env.data.accessToken)
        assertEquals(Role.ADMIN, env.data.user.role)
        assertEquals(OrgType.CARRIER, env.data.user.organizationType)
        assertTrue(env.data.user.rucVerified)
        assertEquals("2026-11-10T20:00:00Z", env.meta?.timestamp)
    }

    @Test fun `request view con subasta y mi oferta`() {
        val r = LogyxJson.decodeFromString<ApiEnvelope<RequestView>>("""{"data":$requestJson,$ts}""").data
        assertEquals("LIMA", r.originCity)
        assertEquals(RequestStatus.PUBLISHED, r.status)
        assertEquals(1010.5, r.distanceKm)
        assertNull(r.volumeM3)
        assertEquals(AuctionStatus.OPEN, r.auction?.status)
        assertEquals(2L, r.auction?.bidCount)
        assertEquals(BidStatus.PENDING, r.myBid?.status)
        assertEquals(1700.5, r.myBid?.amount)
    }

    @Test fun `lista de solicitudes`() {
        val list = LogyxJson.decodeFromString(ListSerializer(RequestView.serializer()), "[$requestJson,$requestJson]")
        assertEquals(2, list.size)
    }

    @Test fun `shipment con paradas e ignora campos desconocidos`() {
        val s = LogyxJson.decodeFromString<ApiEnvelope<ShipmentView>>("""{"data":$shipmentJson,$ts}""").data
        assertEquals(ShipmentStatus.IN_TRANSIT, s.status)
        assertEquals(2, s.stops.size)
        assertEquals(StopType.PICKUP, s.stops[0].type)
        assertEquals(StopStatus.COMPLETED, s.stops[0].status)
        assertNull(s.stops[1].arrivedAt)
        assertNull(s.deliveredAt)
    }

    @Test fun `ciudades, vehiculos y resultado de aceptar`() {
        val cities = LogyxJson.decodeFromString<ApiEnvelope<List<CityItem>>>(
            """{"data":[{"code":"LIMA","name":"Lima","lat":-12.0464,"lng":-77.0428}],$ts}""",
        ).data
        assertEquals(-12.0464, cities.single().lat)
        val v = LogyxJson.decodeFromString<ApiEnvelope<List<VehicleView>>>(
            """{"data":[{"id":"$u1","plate":"ABC-123","type":"TRUCK_MEDIUM","maxWeightKg":5000.0,"maxVolumeM3":24.0,"status":"AVAILABLE"}],$ts}""",
        ).data
        assertEquals(VehicleType.TRUCK_MEDIUM, v.single().type)
        val accept = LogyxJson.decodeFromString<ApiEnvelope<AcceptResult>>(
            """{"data":{"shipmentId":"$u1","acceptedBid":null,"rejectedBidIds":["$u2"]},$ts}""",
        ).data
        assertEquals(listOf(u2), accept.rejectedBidIds)
    }

    @Test fun `contrato aditivo 2026-11-10 - vehiculo en oferta, RUC y vehiculo minimo, ciudad de la organizacion`() {
        val bid = LogyxJson.decodeFromString<pe.logyx.core.model.BidView>(
            """{"id":"$u1","requestId":"$u1","amount":1850.0,"status":"PENDING","vehicleType":"TRUCK_MEDIUM","vehiclePlate":"ASA-101"}""",
        )
        assertEquals(VehicleType.TRUCK_MEDIUM, bid.vehicleType)
        assertEquals("ASA-101", bid.vehiclePlate)
        val req = LogyxJson.decodeFromString<RequestView>(
            requestJson.dropLast(1) + ""","requesterRucVerified":true,"minVehicleType":"REFRIGERATED"}""",
        )
        assertTrue(req.requesterRucVerified)
        assertEquals(VehicleType.REFRIGERATED, req.minVehicleType)
        val user = LogyxJson.decodeFromString<pe.logyx.core.model.UserInfo>(userJson.dropLast(1) + ""","organizationCity":"Juliaca"}""")
        assertEquals("Juliaca", user.organizationCity)
    }

    @Test fun `marketplace envia filtros y pagina leyendo meta`() = runTest {
        var url = ""
        val c = createLogyxHttpClient("http://x/", { "t" }, MockEngine { req ->
            url = req.url.toString()
            respond("""{"data":[$requestJson],"meta":{"timestamp":"2026-11-10T15:00:00Z","page":0,"size":20,"total":37}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val page = LogyxApi(c).marketplacePage(
            pe.logyx.core.model.MarketplaceFilter(originCity = "LIMA", cargoType = pe.logyx.core.model.CargoType.GENERAL, sort = pe.logyx.core.model.MarketplaceSort.PRICE_DESC),
            page = 0,
        )
        assertTrue("originCity=LIMA" in url && "cargoType=GENERAL" in url && "sort=price_desc" in url && "page=0" in url && "size=20" in url, url)
        assertEquals(37, page.total)
        assertEquals(0, page.page)
        assertTrue(page.hasMore)
        assertEquals(1, page.items.size)
    }

    @Test fun `dashboard summary y envio activo 404`() = runTest {
        val s = LogyxApi(client(HttpStatusCode.OK, """{"data":{"role":"CARRIER","compatibleLoads":8,"pendingBids":2,"activeShipments":3,"deliveredThisMonth":9},$ts}""")).dashboardSummary()
        assertEquals(8, s.compatibleLoads)
        assertNull(s.activeRequests)
        val e = assertFailsWith<ApiException> {
            LogyxApi(client(HttpStatusCode.NotFound, """{"error":{"code":"LOGYX-SHP-002","message":"No tienes un envío activo","field":null},$ts}""")).driverActiveShipment()
        }
        assertEquals(404, e.status)
    }

    @Test fun `error de la API se traduce a ApiException`() = runTest {
        val body = """{"error":{"code":"LOGYX-MKT-012","message":"El monto está por debajo del piso","field":"amount"},$ts}"""
        val api = LogyxApi(client(HttpStatusCode.BadRequest, body))
        val e = assertFailsWith<ApiException> {
            api.placeBid(u1, pe.logyx.core.model.PlaceBidRequest(u2, 1.0))
        }
        assertEquals("LOGYX-MKT-012", e.code)
        assertEquals("amount", e.field)
        assertEquals("El monto está por debajo del piso", e.message)
    }

    @Test fun `401 avisa para cerrar sesion y login envia body y desenvuelve data`() = runTest {
        var unauthorized = 0
        val failing = LogyxApi(client(HttpStatusCode.Unauthorized, "{}"), onUnauthorized = { unauthorized++ })
        assertFailsWith<ApiException> { failing.me() }
        assertEquals(1, unauthorized)

        val ok = LogyxApi(client(HttpStatusCode.OK, """{"data":{"accessToken":"t","expiresIn":60,"user":$userJson},$ts}"""))
        assertNotNull(ok.login(" a@b.pe ", "x").user)
    }

    @Test fun `construye la URL y el header Bearer`() = runTest {
        var url = ""; var auth: String? = null
        val c = createLogyxHttpClient("http://10.0.2.2:8080/", { "tok123" }, MockEngine { req ->
            url = req.url.toString(); auth = req.headers[HttpHeaders.Authorization]
            respond("""{"data":[],$ts}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        LogyxApi(c).marketplace()
        assertEquals("http://10.0.2.2:8080/api/v1/shipment-requests", url)
        assertEquals("Bearer tok123", auth)
    }

    private fun client(status: HttpStatusCode, body: String) = createLogyxHttpClient(
        baseUrl = "http://localhost:8080",
        tokenProvider = { "tok" },
        engine = MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )
}
