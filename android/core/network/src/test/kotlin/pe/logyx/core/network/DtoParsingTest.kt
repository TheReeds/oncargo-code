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

    // ───────── Ola 2, zona P: publicar desde el móvil, libreta y plantillas ─────────
    @Test fun `plantilla con programacion, libreta y cotizacion se parsean e ignoran campos nuevos`() {
        val templates = LogyxJson.decodeFromString<ApiEnvelope<List<pe.logyx.core.model.TemplateView>>>(
            """{"data":[{"id":"t1","name":"Quinua","originCity":"JULIACA","destinationCity":"AREQUIPA","cargoType":"GENERAL","weightKg":3000,
            "invitePreferred":true,"archived":false,"recentRuns":[],"campoNuevo":1,
            "schedule":{"frequency":"BIWEEKLY","pickupWeekday":1,"leadDays":2,"skipHolidays":true,"mode":"DRAFT","paused":false,"finished":false,"nextPickupDate":"2026-11-23","failures":0}}]}""",
        ).data
        assertEquals("Quinua", templates.single().name)
        assertEquals("DRAFT", templates.single().schedule?.mode)
        val places = LogyxJson.decodeFromString<ApiEnvelope<List<pe.logyx.core.model.PlaceView>>>(
            """{"data":[{"id":"p1","label":"Almacén","city":"LIMA","address":"Av. Industrial 450","windowFrom":"08:00","windowTo":"17:00","kind":"PICKUP","archived":false}]}""",
        ).data
        assertEquals(pe.logyx.core.model.PlaceKind.PICKUP, places.single().kind)
        val quote = LogyxJson.decodeFromString<ApiEnvelope<pe.logyx.core.model.QuoteView>>(
            """{"data":{"distanceKm":1250.5,"fuelCost":100,"transportCost":900,"tollCost":50,"handlingFee":30,"suggestedPrice":2400,"priceFloor":1800}}""",
        ).data
        assertEquals(1800.0, quote.priceFloor)
    }

    @Test fun `crear solicitud manda la Idempotency-Key y el cuerpo solo con lo que hay`() = runTest {
        var key: String? = null
        var body = ""
        val engine = MockEngine { request ->
            key = request.headers["Idempotency-Key"]
            body = (request.body as io.ktor.http.content.TextContent).text
            respond(
                """{"data":{"id":"r9","originCity":"LIMA","destinationCity":"JULIACA","cargoType":"GENERAL","weightKg":3200,"status":"PUBLISHED","code":"OC-AB123"}}""",
                HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "t" }, engine))
        val created = api.createRequest(
            pe.logyx.core.model.CreateRequestBody("LIMA", "Av. Argentina 1234", "JULIACA", "Jr. Lima 456", pe.logyx.core.model.CargoType.GENERAL, "Tela", 3200.0, null, "2026-11-23",
                pickupContactPhone = "987654321", pickupWindowFrom = "08:00", pickupWindowTo = "12:00"),
            idempotencyKey = "abc-123",
        )
        assertEquals("OC-AB123", created.code)
        assertEquals("abc-123", key)
        assertTrue(body.contains("\"pickupContactPhone\":\"987654321\""))
        assertTrue(!body.contains("deliveryContactName"), "los nulos no viajan")
    }

    // --- Ola 2, zona T (V22): búsquedas guardadas, posición aproximada, avisos y sellos de la oferta ---

    @Test fun `busqueda guardada se parsea con filtros opcionales y campos nuevos`() {
        val list = LogyxJson.decodeFromString<ApiEnvelope<List<pe.logyx.core.model.SavedSearch>>>(
            """{"data":[{"id":"$u1","name":"Lima a Juliaca","originCity":"LIMA","destinationCity":"JULIACA","cargoType":"PERISHABLE",
            "pickupFrom":"2026-11-28","pickupTo":"2026-12-05","maxWeightKg":8000.0,"vehicleId":"$u2","active":true,"createdAt":"2026-11-19T10:00:00-05:00","campoNuevo":1},
            {"id":"$u2","name":"Solo tipo","cargoType":"UN_TIPO_NUEVO","active":false}],$ts}""",
        ).data
        val a = list[0]
        assertEquals("Lima a Juliaca", a.name)
        assertEquals(pe.logyx.core.model.CargoType.PERISHABLE, a.cargoType)
        assertEquals(8000.0, a.maxWeightKg)
        assertEquals(u2, a.vehicleId)
        assertTrue(a.active)
        val b = list[1]
        assertNull(b.originCity)
        assertNull(b.cargoType, "un tipo de carga desconocido no rompe la lista")
        assertTrue(!b.active)
    }

    @Test fun `busquedas guardadas - crear, pausar y borrar usan los endpoints del contrato`() = runTest {
        val calls = mutableListOf<String>()
        var lastBody = ""
        val engine = MockEngine { req ->
            calls += "${req.method.value} ${req.url.encodedPath}"
            (req.body as? io.ktor.http.content.TextContent)?.let { lastBody = it.text }
            when (req.method.value) {
                "DELETE" -> respond("""{"data":{},$ts}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                "GET" -> respond("""{"data":[{"id":"$u1","name":"A","originCity":"LIMA","active":true}],$ts}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("""{"data":{"id":"$u1","name":"A","originCity":"LIMA","active":false},$ts}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val api = LogyxApi(createLogyxHttpClient("http://localhost", { "t" }, engine))
        assertEquals(1, api.savedSearches().size)
        api.createSavedSearch(pe.logyx.core.model.SavedSearchRequest("A", originCity = "LIMA"))
        assertTrue(lastBody.contains("\"originCity\":\"LIMA\""))
        assertTrue(!lastBody.contains("destinationCity"), "los filtros vacíos no viajan")
        val paused = api.updateSavedSearch(u1, pe.logyx.core.model.SavedSearch(u1, "A", originCity = "LIMA").toRequest(active = false))
        assertTrue(!paused.active)
        assertTrue(lastBody.contains("\"active\":false"))
        api.deleteSavedSearch(u1)
        assertEquals(
            listOf("GET /api/v1/saved-searches", "POST /api/v1/saved-searches", "PUT /api/v1/saved-searches/$u1", "DELETE /api/v1/saved-searches/$u1"),
            calls,
        )
    }

    @Test fun `error ALT-002 de busquedas guardadas llega en espanol con su codigo`() = runTest {
        val api = LogyxApi(
            client(
                HttpStatusCode.Conflict,
                """{"error":{"code":"LOGYX-ALT-002","message":"Llegaste al máximo de 20 búsquedas guardadas."},$ts}""",
            ),
        )
        val e = assertFailsWith<ApiException> { api.createSavedSearch(pe.logyx.core.model.SavedSearchRequest("A", originCity = "LIMA")) }
        assertEquals("LOGYX-ALT-002", e.code)
        assertEquals("Llegaste al máximo de 20 búsquedas guardadas.", e.message)
    }

    @Test fun `posicion aproximada de la oferta, con valores desconocidos tolerados`() {
        fun standing(position: String) = LogyxJson.decodeFromString<pe.logyx.core.model.MyBidStanding>(
            """{"rank":2,"bidCount":5,"isBest":false,"vsBest":"WITHIN_5_PCT","finalStanding":false$position}""",
        )
        assertEquals(pe.logyx.core.model.BidPosition.TOP_TWO, standing(""","position":"TOP_TWO"""").position)
        assertEquals(pe.logyx.core.model.BidPosition.LOWER_HALF, standing(""","position":"LOWER_HALF"""").position)
        assertEquals(pe.logyx.core.model.BidPosition.UPPER_HALF, standing(""","position":"UPPER_HALF"""").position)
        assertNull(standing("").position, "campo ausente")
        assertNull(standing(""","position":null""").position)
        assertNull(standing(""","position":"MITAD_NUEVA"""").position, "un valor nuevo no rompe el parseo")
        assertEquals(pe.logyx.core.model.BidStandingBand.WITHIN_5_PCT, standing(""","position":"MITAD_NUEVA"""").vsBest)
    }

    @Test fun `oferta con mejoras, sellos de documentos y avisos FLT se parsea`() {
        val bid = LogyxJson.decodeFromString<pe.logyx.core.model.BidView>(
            """{"id":"$u1","requestId":"$u2","amount":1650.0,"status":"PENDING","improvedCount":2,"improvedAt":"2026-11-19T11:00:00-05:00",
            "soatValidUntil":"2027-03-01","inspectionValidUntil":"2026-12-10","campoNuevo":"x",
            "warnings":[{"code":"LOGYX-CAP-006","message":"El vehículo ya tiene ofertas para esas fechas."},
            {"code":"LOGYX-FLT-007","message":"El SOAT de ABC-123 está vencido."},{"code":"LOGYX-FLT-008","message":"La inspección vence antes del recojo."}]}""",
        )
        assertEquals(2, bid.improvedCount)
        assertEquals("2027-03-01", bid.soatValidUntil)
        assertEquals("2026-12-10", bid.inspectionValidUntil)
        assertEquals(listOf("LOGYX-CAP-006", "LOGYX-FLT-007", "LOGYX-FLT-008"), bid.warnings.map { it.code })
        // una oferta anterior a V22 sigue parseando
        val old = LogyxJson.decodeFromString<pe.logyx.core.model.BidView>("""{"id":"$u1","requestId":"$u2","amount":1000.0}""")
        assertEquals(0, old.improvedCount)
        assertNull(old.soatValidUntil)
        assertTrue(old.warnings.isEmpty())
    }

    @Test fun `avisos nuevos de V22 y tipos desconocidos no rompen la lista`() {
        val list = LogyxJson.decodeFromString<ApiEnvelope<List<pe.logyx.core.model.NotificationView>>>(
            """{"data":[
            {"id":"n1","type":"NEW_LOAD_MATCH","title":"Nueva carga","body":"Lima a Juliaca","relatedType":"REQUEST","relatedId":"$u1","read":false},
            {"id":"n2","type":"FLEET_DOCUMENT_EXPIRING","title":"SOAT por vencer","body":"ABC-123","relatedType":"VEHICLE","relatedId":"$u2","read":false},
            {"id":"n3","type":"FLEET_DOCUMENT_EXPIRING","title":"Licencia por vencer","body":"Juan","relatedType":"DRIVER","relatedId":"$u2","read":false},
            {"id":"n4","type":"BID_IMPROVED","title":"Mejoraron una oferta","body":"x","relatedType":"REQUEST","relatedId":"$u1","read":true},
            {"id":"n5","type":"TIPO_DEL_FUTURO","title":"Algo","body":"y","relatedType":"ALGO_NUEVO","relatedId":"$u1","read":false}],$ts}""",
        ).data
        assertEquals(pe.logyx.core.model.NotificationType.NEW_LOAD_MATCH, list[0].type)
        assertEquals(pe.logyx.core.model.NotificationTarget.REQUEST, list[0].relatedType)
        assertEquals(pe.logyx.core.model.NotificationTarget.VEHICLE, list[1].relatedType)
        assertEquals(pe.logyx.core.model.NotificationTarget.DRIVER, list[2].relatedType)
        assertEquals(pe.logyx.core.model.NotificationType.BID_IMPROVED, list[3].type)
        assertEquals(pe.logyx.core.model.NotificationType.UNKNOWN, list[4].type)
        assertNull(list[4].relatedType)
    }
}
