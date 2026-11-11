package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.CounterOfferStatus
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentStatus
import pe.logyx.core.model.NotificationTarget
import pe.logyx.core.model.NotificationType
import pe.logyx.core.model.ReviewDirection
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.BidView
import pe.logyx.core.model.RequestView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ejemplos de docs/api/CHANGELOG.md (fase 2) contra los DTOs. */
class Phase2ParsingTest {
    private val ts = """"meta":{"timestamp":"2026-11-10T20:00:00Z"}"""
    private val u = "11111111-1111-1111-1111-111111111111"

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String, capture: (io.ktor.client.request.HttpRequestData) -> Unit = {}): LogyxApi {
        val engine = MockEngine { req ->
            capture(req)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
    }

    @Test fun `campos nuevos en bid request y shipment`() {
        val bid = LogyxJson.decodeFromString<BidView>("""{"id":"$u","requestId":"$u","amount":1850.0,"status":"PENDING","carrierTrustScore":77.8,"assignedByOperator":false}""")
        assertEquals(77.8, bid.carrierTrustScore)
        val req = LogyxJson.decodeFromString<RequestView>("""{"id":"$u","originCity":"LIMA","destinationCity":"JULIACA","cargoType":"GENERAL","weightKg":1.0,"status":"PUBLISHED","requesterTrustScore":82.5}""")
        assertEquals(82.5, req.requesterTrustScore)
        val sh = LogyxJson.decodeFromString<ShipmentView>("""{"id":"$u","originCity":"LIMA","destinationCity":"JULIACA","status":"IN_TRANSIT","hasActiveIncident":true}""")
        assertTrue(sh.hasActiveIncident)
    }

    @Test fun `hilos y mensajes con cursor`() = runTest {
        val threads = api(body = """{"data":[{"requestId":"$u","carrierOrgId":"$u","carrierName":"Sur Andino","bidId":"$u","bidAmount":1850.00,"bidStatus":"PENDING","messageCount":3,"lastMessage":"Hola","lastMessageAt":"2026-11-10T15:02:11Z"}],$ts}""").threads(u)
        assertEquals(3, threads.single().messageCount)
        val page = api(body = """{"data":[{"id":"$u","requestId":"$u","carrierOrgId":"$u","senderOrgId":"$u","senderName":"Comercial","mine":true,"body":"Sí","proposedPrice":null,"system":false,"createdAt":"2026-11-10T15:01:00Z"}],"meta":{"timestamp":"x","size":30,"nextCursor":"9d3c"}}""").messages(u, u)
        assertEquals("9d3c", page.nextCursor)
        assertTrue(page.items.single().mine)
    }

    @Test fun `RN07 llega como error con field body`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.UnprocessableEntity, """{"error":{"code":"LOGYX-NEG-001","field":"body","message":"El mensaje contiene datos de contacto"},$ts}""").postMessage(u, u, "999 888 777")
        }
        assertEquals("LOGYX-NEG-001", e.code)
        assertEquals("body", e.field)
    }

    @Test fun `contraoferta`() = runTest {
        val co = api(HttpStatusCode.Created, """{"data":{"id":"$u","bidId":"$u","requestId":"$u","carrierOrgId":"$u","offeredByOrgId":"$u","previousAmount":1850.00,"amount":1700.00,"message":"¿Lo dejamos?","status":"PENDING","createdAt":"x","respondedAt":null},$ts}""").proposeCounterOffer(u, 1700.0, "¿Lo dejamos?")
        assertEquals(CounterOfferStatus.PENDING, co.status)
        assertEquals(1700.0, co.amount)
    }

    @Test fun `documento y multipart`() = runTest {
        var contentType: String? = null
        var bodyText = ""
        val engine = MockEngine { req ->
            contentType = req.body.contentType?.toString()
            bodyText = req.body.toByteArray().decodeToString()
            respond(
                """{"data":{"id":"$u","shipmentId":"$u","stopId":"$u","type":"DELIVERY_PHOTO","fileName":"entrega.jpg","contentType":"image/jpeg","sizeBytes":182340,"uploadedByOrgId":"$u","createdAt":"x","downloadUrl":"/api/v1/documents/$u/file"},$ts}""",
                HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val doc = LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
            .uploadDocument(u, byteArrayOf(1, 2, 3), "entrega.jpg", "image/jpeg", DocumentType.DELIVERY_PHOTO, stopId = u)
        assertEquals(DocumentType.DELIVERY_PHOTO, doc.type)
        assertTrue(doc.isImage)
        assertTrue(contentType!!.startsWith("multipart/form-data"))
        assertTrue(bodyText.contains("DELIVERY_PHOTO"))
        assertTrue(bodyText.contains("filename"))
        assertTrue(bodyText.contains("stopId"))
    }

    @Test fun `TRK-006 sin foto`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.Conflict, """{"error":{"code":"LOGYX-TRK-006","message":"Falta la foto"},$ts}""").completeStop(u, u)
        }
        assertEquals("LOGYX-TRK-006", e.code)
    }

    @Test fun `reputacion y calificaciones`() = runTest {
        val rep = api(body = """{"data":{"organizationId":"$u","legalName":"Sur Andino","type":"CARRIER","rucVerified":true,"city":"Arequipa","trustScore":77.8,"totalReviews":1,"averageRating":4.67,"averagePunctuality":5.00,"averageCargoCondition":4.00,"averageCommunication":5.00,"priorMean":75,"priorWeight":5,"recentReviews":[{"id":"$u","shipmentId":"$u","reviewerOrgId":"$u","reviewerName":"Comercial","reviewedOrgId":"$u","direction":"PYME_TO_CARRIER","punctuality":5,"cargoCondition":4,"communication":5,"rating":4.67,"comment":"Puntual","createdAt":"x"}]},$ts}""").reputation(u)
        assertEquals(77.8, rep.trustScore)
        assertEquals(ReviewDirection.PYME_TO_CARRIER, rep.recentReviews.single().direction)
        val s = api(body = """{"data":{"canReview":false,"blockedCode":"LOGYX-REV-001","blockedReason":"El envío aún no ha sido entregado (RN06)","reviews":[]},$ts}""").shipmentReviews(u)
        assertEquals(false, s.canReview)
        assertNotNull(s.blockedReason)
    }

    @Test fun `incidencia con comentarios`() = runTest {
        val i = api(body = """{"data":{"id":"$u","shipmentId":"$u","requesterOrgId":"$u","requesterName":"A","carrierOrgId":"$u","carrierName":"B","reportedByOrgId":"$u","category":"CARGO_DAMAGE","description":"Dos bolsas rotas","status":"OPEN","active":true,"resolutionNotes":null,"createdAt":"x","updatedAt":"x","resolvedAt":null,"comments":[{"id":"$u","authorOrgId":"$u","authorName":"LOGYX","body":"Revisando","createdAt":"x"}]},$ts}""").incident(u)
        assertEquals(IncidentCategory.CARGO_DAMAGE, i.category)
        assertEquals(IncidentStatus.OPEN, i.status)
        assertTrue(i.active)
        assertEquals("Revisando", i.comments.single().body)
    }

    @Test fun `notificaciones y contador`() = runTest {
        val p = api(body = """{"data":[{"id":"$u","type":"NEW_BID","title":"Nueva oferta recibida","body":"x","relatedType":"REQUEST","relatedId":"$u","read":false,"readAt":null,"createdAt":"x"}],"meta":{"timestamp":"x","page":0,"size":20,"total":15}}""").notifications()
        assertEquals(15, p.total)
        assertEquals(NotificationType.NEW_BID, p.items.single().type)
        assertEquals(NotificationTarget.REQUEST, p.items.single().relatedType)
        assertEquals(3, api(body = """{"data":{"unread":3},$ts}""").unreadCount().unread)
        assertEquals(3, api(body = """{"data":{"updated":3},$ts}""").markAllNotificationsRead().updated)
    }

    @Test fun `descarga binaria`() = runTest {
        val engine = MockEngine { respond(byteArrayOf(9, 8, 7), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg")) }
        val bytes = LogyxApi(createLogyxHttpClient("http://localhost", { null }, engine)).documentFile(u)
        assertEquals(3, bytes.size)
        assertNull(null)
    }
}
