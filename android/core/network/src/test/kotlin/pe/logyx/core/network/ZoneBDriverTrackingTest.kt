package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.ShareLinkCreated
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopView
import pe.logyx.core.model.hasContactInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ola 1, zona B (roadmap v2 1.3 y 1.11): contactos por parada, hora del dispositivo, código y enlaces de seguimiento. */
class ZoneBDriverTrackingTest {
    private val u = "11111111-1111-1111-1111-111111111111"
    private val ts = """"meta":{"timestamp":"2026-11-18T10:00:00Z"}"""
    private inline fun <reified T> parse(json: String): T = LogyxJson.decodeFromString<T>(json)

    private data class Seen(var method: String = "", var path: String = "", var body: String = "", var contentType: String? = null)

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String, seen: Seen = Seen()): LogyxApi {
        val engine = MockEngine { req: HttpRequestData ->
            seen.method = req.method.value; seen.path = req.url.encodedPath; seen.body = req.body.toByteArray().decodeToString()
            seen.contentType = req.body.contentType?.toString()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
    }

    private val stopJson = """{"id":"$u","sequence":2,"type":"DELIVERY","city":"JULIACA","address":"Jr. Lima 456","status":"ARRIVED",
        "arrivedAt":"2026-11-18T08:10:00-05:00","arrivedRecordedAt":"2026-11-18T08:40:12-05:00","completedAt":null,"completedRecordedAt":null,
        "contactName":"Ana Torres","contactPhone":"987654321","windowFrom":"08:00","windowTo":"12:00","instructions":"Puerta lateral"}"""

    private val shipmentJson = """{"id":"$u","code":"OC-7K2QX","customerReference":"4500123","requesterOrgId":"$u","carrierOrgId":"$u",
        "originCity":"LIMA","destinationCity":"JULIACA","status":"IN_TRANSIT","stops":[$stopJson],
        "priceBreakdown":{"agreedPrice":1850.0,"commission":92.5}}"""

    @Test fun `StopView trae contacto, ventana, instrucciones y las horas registradas`() {
        val s = parse<StopView>(stopJson)
        assertEquals("Ana Torres", s.contactName)
        assertEquals("987654321", s.contactPhone)
        assertEquals("08:00", s.windowFrom)
        assertEquals("12:00", s.windowTo)
        assertEquals("Puerta lateral", s.instructions)
        assertEquals("2026-11-18T08:10:00-05:00", s.arrivedAt)
        assertEquals("2026-11-18T08:40:12-05:00", s.arrivedRecordedAt)
        assertNull(s.completedRecordedAt)
        assertTrue(s.hasContactInfo)
    }

    @Test fun `ShipmentView trae codigo y referencia y tolera campos que no modela`() {
        val sh = parse<ShipmentView>(shipmentJson)
        assertEquals("OC-7K2QX", sh.code)
        assertEquals("4500123", sh.customerReference)
        assertEquals(u, sh.requesterOrgId)
        assertEquals(u, sh.carrierOrgId)
        assertEquals("Ana Torres", sh.stops.single().contactName)
    }

    @Test fun `un payload anterior sin los campos nuevos sigue parseando`() {
        val old = parse<ShipmentView>(
            """{"id":"$u","originCity":"LIMA","destinationCity":"JULIACA","status":"PENDING",
            "stops":[{"id":"$u","sequence":1,"type":"PICKUP","city":"LIMA","status":"PENDING"}]}""",
        )
        assertNull(old.code); assertNull(old.customerReference); assertNull(old.carrierOrgId)
        val stop = old.stops.single()
        assertNull(stop.contactName); assertNull(stop.windowFrom); assertNull(stop.arrivedRecordedAt)
        assertFalse(stop.hasContactInfo)
        assertEquals(StopStatus.PENDING, stop.status)
    }

    @Test fun `arrive con occurredAt manda el cuerpo JSON`() = runTest {
        val seen = Seen()
        api(body = """{"data":$shipmentJson,$ts}""", seen = seen).arriveAtStop(u, u, "2026-11-18T08:10:00-05:00")
        assertEquals("POST /api/v1/shipments/$u/stops/$u/arrive", seen.method + " " + seen.path)
        assertEquals("""{"occurredAt":"2026-11-18T08:10:00-05:00"}""", seen.body)
        assertTrue(seen.contentType.orEmpty().startsWith("application/json"), seen.contentType)
    }

    @Test fun `complete sin occurredAt va sin cuerpo (hora del servidor)`() = runTest {
        val seen = Seen()
        val sh = api(body = """{"data":$shipmentJson,$ts}""", seen = seen).completeStop(u, u)
        assertEquals("/api/v1/shipments/$u/stops/$u/complete", seen.path)
        assertEquals("", seen.body)
        assertEquals("OC-7K2QX", sh.code)
    }

    @Test fun `TRK-008 llega con field occurredAt`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(
                HttpStatusCode.BadRequest,
                """{"error":{"code":"LOGYX-TRK-008","field":"occurredAt","message":"La hora del evento no es válida"},$ts}""",
            ).arriveAtStop(u, u, "2020-01-01T00:00:00-05:00")
        }
        assertEquals("LOGYX-TRK-008", e.code)
        assertEquals("occurredAt", e.field)
        assertEquals(400, e.status)
    }

    @Test fun `crear enlace de seguimiento manda la vigencia y devuelve la url`() = runTest {
        val seen = Seen()
        val body = """{"data":{"link":{"id":"$u","tokenHint":"x9Qa","status":"ACTIVE","createdAt":"2026-11-18T10:00:00Z",
            "expiresAt":"2026-11-25T10:00:00Z","revokedAt":null,"lastViewedAt":null,"views":0},
            "token":"secret-token","url":"https://oncargo.pe/seguimiento/secret-token"},$ts}"""
        val created: ShareLinkCreated = api(HttpStatusCode.Created, body, seen).createShareLink(u, 7)
        assertEquals("POST /api/v1/shipments/$u/share-links", seen.method + " " + seen.path)
        assertEquals("""{"expiresInDays":7}""", seen.body)
        assertEquals("https://oncargo.pe/seguimiento/secret-token", created.url)
        assertEquals("x9Qa", created.link.tokenHint)
        assertEquals(0, created.link.views)

        val sin = Seen()
        api(HttpStatusCode.Created, body, sin).createShareLink(u)
        assertEquals("{}", sin.body)
    }

    @Test fun `SHL-004 con diez enlaces activos`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.Conflict, """{"error":{"code":"LOGYX-SHL-004","message":"Ya tienes 10 enlaces activos"},$ts}""").createShareLink(u)
        }
        assertEquals("LOGYX-SHL-004", e.code)
        assertEquals("Ya tienes 10 enlaces activos", e.message)
    }
}
