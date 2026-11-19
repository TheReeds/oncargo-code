package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.AdjustmentStatus
import pe.logyx.core.model.AdjustmentType
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DocumentView
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentSeverity
import pe.logyx.core.model.IncidentView
import pe.logyx.core.model.ProposeAdjustmentRequest
import pe.logyx.core.model.ReceiverDoc
import pe.logyx.core.model.ReportIncidentRequest
import pe.logyx.core.model.ShipmentAdjustmentsView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopEventRequest
import pe.logyx.core.model.informative
import pe.logyx.core.model.isValidLoadedWeight
import pe.logyx.core.model.parseWeightKg
import pe.logyx.core.model.registeredByOffice
import pe.logyx.core.model.reportableIncidentCategories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ola 2, zona X (roadmap 2.5–2.7): contrato de V23 (paradas, ajustes, incidencias v2) visto desde Android. */
class ZoneXWave2ParsingTest {
    private val u = "11111111-1111-1111-1111-111111111111"
    private val ts = """"meta":{"timestamp":"2026-11-19T10:00:00Z"}"""
    private inline fun <reified T> parse(json: String): T = LogyxJson.decodeFromString<T>(json)

    private class Seen(var path: String = "", var body: String = "", var key: String? = null, var hasBody: Boolean = false)

    private fun api(body: String, seen: Seen = Seen(), status: HttpStatusCode = HttpStatusCode.OK) = LogyxApi(
        createLogyxHttpClient("http://localhost", { "tok" }, MockEngine { req ->
            seen.path = req.url.encodedPath
            seen.body = req.body.toByteArray().decodeToString()
            seen.hasBody = req.body.contentType != null
            seen.key = req.headers["Idempotency-Key"]
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
    )

    private val shipmentJson = """{"id":"$u","originCity":"LIMA","destinationCity":"JULIACA","status":"DELIVERED","agreedPrice":1850.00,
        "pickupPhotoRequired":true,"pickupWeightRequired":true,"adjustmentsTotal":120.00,"pendingAdjustments":1,"finalPrice":1970.00,
        "priceBreakdown":{"amount":1850.00,"vatRate":0.18,"netOfVat":1567.80,"vat":282.20,"commissionRate":null},
        "finalPriceBreakdown":{"amount":1970.00,"vatRate":0.18,"netOfVat":1669.49,"vat":300.51,"commissionRate":0.05,"commission":83.47,"commissionVat":15.02,"carrierNet":1871.51},
        "stops":[
          {"id":"p","sequence":1,"type":"PICKUP","city":"LIMA","status":"COMPLETED","loadedWeightKg":2450.50,
           "arrivalOfficeReason":null,"completionOfficeReason":"Celular sin batería"},
          {"id":"d","sequence":2,"type":"DELIVERY","city":"JULIACA","status":"COMPLETED","receivedByName":"Ana Torres","receivedByDoc":"45678912"}
        ]}"""

    @Test fun `ShipmentView y StopView traen recojo, receptor, oficina y precio final`() {
        val s = parse<ShipmentView>(shipmentJson)
        assertTrue(s.pickupPhotoRequired)
        assertTrue(s.pickupWeightRequired)
        assertEquals(120.0, s.adjustmentsTotal)
        assertEquals(1, s.pendingAdjustments)
        assertEquals(1970.0, s.finalPrice)
        assertEquals(1871.51, s.finalPriceBreakdown?.carrierNet)
        assertNull(s.priceBreakdown?.commissionRate, "la PYME no recibe la comisión")
        val (pickup, delivery) = s.stops
        assertEquals(2450.5, pickup.loadedWeightKg)
        assertEquals("Celular sin batería", pickup.completionOfficeReason)
        assertTrue(pickup.registeredByOffice)
        assertEquals("Ana Torres", delivery.receivedByName)
        assertEquals("45678912", delivery.receivedByDoc)
        assertFalse(delivery.registeredByOffice)
    }

    @Test fun `un backend anterior sin los campos nuevos sigue leyendose`() {
        val s = parse<ShipmentView>("""{"id":"$u","originCity":"LIMA","destinationCity":"PUNO","status":"PENDING"}""")
        assertFalse(s.pickupPhotoRequired)
        assertEquals(0, s.pendingAdjustments)
        assertNull(s.finalPrice)
    }

    @Test fun `ajustes del envio con sugerencias y valores desconocidos`() {
        val v = parse<ShipmentAdjustmentsView>(
            """{"agreedPrice":1850.00,"acceptedTotal":120.00,"finalPrice":1970.00,"finalPriceBreakdown":{"amount":1970.00,"vat":300.51},
            "adjustments":[
              {"id":"a1","shipmentId":"$u","stopId":"d","type":"DETENTION","amount":120.00,"note":"4 h de espera","status":"ACCEPTED",
               "createdAt":"2026-11-18T15:00:00-05:00","respondBy":"2026-11-20T15:00:00-05:00","escalated":false,"decidedAt":"2026-11-18T16:00:00-05:00"},
              {"id":"a2","type":"FUEL_SURCHARGE","amount":-30.00,"status":"ON_HOLD","escalated":true},
              {"id":"a3","type":"WEIGHT_DIFF","amount":-50.00,"status":"REJECTED","decisionNote":"El peso fue el declarado"}
            ],
            "suggestions":[{"type":"DETENTION","stopId":"d","amount":90.00,"detail":"Estadía de 4 h (franquicia 3 h)"}]}""",
        )
        assertEquals(1970.0, v.finalPrice)
        assertEquals(AdjustmentType.DETENTION, v.adjustments[0].type)
        assertEquals(AdjustmentStatus.ACCEPTED, v.adjustments[0].status)
        assertEquals(AdjustmentType.UNKNOWN, v.adjustments[1].type, "tipo nuevo del servidor")
        assertEquals(AdjustmentStatus.UNKNOWN, v.adjustments[1].status)
        assertTrue(v.adjustments[1].escalated)
        assertEquals("El peso fue el declarado", v.adjustments[2].decisionNote)
        assertEquals(90.0, v.suggestions.single().amount)
    }

    @Test fun `incidencia v2 con severidad, adjuntos y categorias desconocidas`() {
        val i = parse<IncidentView>(
            """{"id":"$u","shipmentId":"$u","category":"ROAD_BLOCKED","description":"Huaico en Ticlio","status":"OPEN","active":true,
            "severity":"MEDIUM","blocking":false,"holdsShipment":false,"claimedAmount":null,"declaredValue":15000.00,
            "dueAt":"2026-11-19T18:00:00-05:00","slaBreached":false,"withdrawnAt":null,"reopenCount":1,
            "latitude":-11.6,"longitude":-76.2,"occurredAt":"2026-11-19T09:00:00-05:00","canWithdraw":true,
            "attachments":[{"documentId":"$u","fileName":"foto.jpg","type":"OTHER","contentType":"image/jpeg"}],
            "comments":[{"id":"c1","body":"Revisando","internal":true}]}""",
        )
        assertEquals(IncidentCategory.ROAD_BLOCKED, i.category)
        assertTrue(i.category.informative)
        assertEquals(IncidentSeverity.MEDIUM, i.severity)
        assertFalse(i.blocking)
        assertTrue(i.canWithdraw)
        assertEquals(15000.0, i.declaredValue)
        assertEquals(-11.6, i.latitude)
        assertEquals("foto.jpg", i.attachments.single().fileName)
        assertTrue(i.comments.single().internal)

        val unknown = parse<IncidentView>("""{"id":"x","shipmentId":"s","category":"VOLCANO","severity":"APOCALYPTIC","description":"?","status":"OPEN"}""")
        assertEquals(IncidentCategory.UNKNOWN, unknown.category)
        assertNull(unknown.severity)
        assertTrue(unknown.blocking, "sin el campo (backend anterior) se asume que congela")
        assertFalse(IncidentCategory.UNKNOWN in reportableIncidentCategories)
        assertEquals(14, reportableIncidentCategories.size)
        assertFalse(IncidentCategory.ACCIDENT.informative)
    }

    @Test fun `documento PICKUP_PHOTO`() {
        val d = parse<DocumentView>("""{"id":"d1","stopId":"p","type":"PICKUP_PHOTO","contentType":"image/jpeg"}""")
        assertEquals(DocumentType.PICKUP_PHOTO, d.type)
        assertEquals(DocumentType.OTHER, parse<DocumentView>("""{"id":"d2","type":"WAYBILL_XML"}""").type, "un tipo nuevo cae en OTHER")
    }

    @Test fun `los cuerpos nuevos no envian los null`() {
        assertEquals("""{"weightKg":2450.5}""", LogyxJson.encodeToString(StopEventRequest.serializer(), StopEventRequest(weightKg = 2450.5)))
        assertTrue(StopEventRequest().isEmpty)
        val report = LogyxJson.encodeToString(
            ReportIncidentRequest.serializer(),
            ReportIncidentRequest(IncidentCategory.ACCIDENT, "Choque", IncidentSeverity.CRITICAL, latitude = -15.5, longitude = -70.1, occurredAt = "2026-11-19T09:00:00-05:00"),
        )
        assertEquals(
            """{"category":"ACCIDENT","description":"Choque","severity":"CRITICAL","latitude":-15.5,"longitude":-70.1,"occurredAt":"2026-11-19T09:00:00-05:00"}""",
            report,
        )
        assertEquals(
            """{"type":"TOLL","amount":45.5}""",
            LogyxJson.encodeToString(ProposeAdjustmentRequest.serializer(), ProposeAdjustmentRequest(AdjustmentType.TOLL, 45.5)),
        )
    }

    @Test fun `cerrar la parada con datos manda el cuerpo y sin datos va vacio`() = runTest {
        val ok = """{"data":$shipmentJson,$ts}"""
        val seen = Seen()
        api(ok, seen).completeStop("s1", "d", StopEventRequest("2026-11-19T09:00:00-05:00", receivedByName = "Ana Torres", receivedByDoc = "45678912"))
        assertEquals("/api/v1/shipments/s1/stops/d/complete", seen.path)
        assertEquals("""{"occurredAt":"2026-11-19T09:00:00-05:00","receivedByName":"Ana Torres","receivedByDoc":"45678912"}""", seen.body)

        val empty = Seen()
        api(ok, empty).completeStop("s1", "d", StopEventRequest())
        assertEquals("", empty.body, "sin datos no hay cuerpo (hora del servidor)")
    }

    @Test fun `reportar incidencia manda la Idempotency-Key y retirar usa su ruta`() = runTest {
        val incident = """{"data":{"id":"i1","shipmentId":"s1","category":"ACCIDENT","description":"Choque","status":"OPEN","severity":"CRITICAL"},$ts}"""
        val seen = Seen()
        val created = api(incident, seen, HttpStatusCode.Created).reportIncident("s1", ReportIncidentRequest(IncidentCategory.ACCIDENT, "Choque"), "emg-1234567890")
        assertEquals("/api/v1/shipments/s1/incidents", seen.path)
        assertEquals("emg-1234567890", seen.key)
        assertEquals(IncidentSeverity.CRITICAL, created.severity)

        val w = Seen()
        api(incident, w).withdrawIncident("i1")
        assertEquals("/api/v1/incidents/i1/withdraw", w.path)
    }

    @Test fun `ajustes - rutas, clave y nota`() = runTest {
        val adj = """{"data":{"id":"a1","type":"TOLL","amount":45.5,"status":"PENDING"},$ts}"""
        val p = Seen()
        api(adj, p, HttpStatusCode.Created).proposeAdjustment("s1", ProposeAdjustmentRequest(AdjustmentType.TOLL, 45.5), "adj-12345678")
        assertEquals("/api/v1/shipments/s1/adjustments", p.path)
        assertEquals("adj-12345678", p.key)
        val r = Seen()
        api(adj, r).rejectAdjustment("a1", "  No hubo peaje  ")
        assertEquals("/api/v1/adjustments/a1/reject", r.path)
        assertEquals("""{"note":"No hubo peaje"}""", r.body)
        val a = Seen()
        api(adj, a).acceptAdjustment("a1")
        assertEquals("/api/v1/adjustments/a1/accept", a.path)
        assertEquals("{}", a.body)
    }

    @Test fun `validacion local de peso y documento de quien recibe`() {
        assertEquals(2450.5, parseWeightKg("2,450.5"))
        assertEquals(2450.5, parseWeightKg("2450,5"))
        assertNull(parseWeightKg("abc"))
        assertTrue(isValidLoadedWeight(1.0))
        assertFalse(isValidLoadedWeight(0.5))
        assertFalse(isValidLoadedWeight(60_001.0))
        assertTrue(ReceiverDoc.isValid("45678912"))
        assertTrue(ReceiverDoc.isValid("456.789-12"), "se normaliza como el backend")
        assertTrue(ReceiverDoc.isValid("ce0012345"))
        assertEquals("CE0012345", ReceiverDoc.normalize("ce 0012345"))
        assertFalse(ReceiverDoc.isValid("1234567"))
        assertFalse(ReceiverDoc.isValid("1234567890123"))
    }
}
