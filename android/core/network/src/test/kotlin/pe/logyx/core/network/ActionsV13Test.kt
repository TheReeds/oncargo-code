package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.AcceptSmartLoadRequest
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.SmartLoadItemAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Endpoints de acción de V13 (retirar, cancelar con motivo, reabrir, republicar, vehículo, relevo y Smart Load en lote). */
class ActionsV13Test {
    private val u = "11111111-1111-1111-1111-111111111111"
    private val ts = """"meta":{"timestamp":"2026-11-17T10:00:00Z"}"""

    private data class Seen(var method: String = "", var path: String = "", var body: String = "")

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String, seen: Seen = Seen()): LogyxApi {
        val engine = MockEngine { req: HttpRequestData ->
            seen.method = req.method.value; seen.path = req.url.encodedPath; seen.body = req.body.toByteArray().decodeToString()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
    }

    private val shipment = """{"data":{"id":"$u","originCity":"LIMA","destinationCity":"JULIACA","status":"CANCELLED","cancellationReason":"Se averió el camión","cancelledAt":"2026-11-17T10:00:00-05:00","cancelledByOrgId":"$u","pickupDate":"2026-11-20","vehicleId":"$u"},$ts}"""
    private val request = """{"data":{"id":"$u","originCity":"LIMA","destinationCity":"JULIACA","cargoType":"GENERAL","weightKg":1000,"status":"PUBLISHED"},$ts}"""

    @Test fun `retirar oferta es POST sin cuerpo y devuelve la oferta WITHDRAWN`() = runTest {
        val seen = Seen()
        val bid = api(body = """{"data":{"id":"$u","requestId":"$u","amount":1500,"status":"WITHDRAWN"},$ts}""", seen = seen).withdrawBid(u)
        assertEquals("POST /api/v1/bids/$u/withdraw", seen.method + " " + seen.path)
        assertEquals(BidStatus.WITHDRAWN, bid.status)
    }

    @Test fun `BID-005 al retirar una oferta que ya no admite cambios`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.Conflict, """{"error":{"code":"LOGYX-BID-005","message":"La solicitud ya no admite cambios en las ofertas"},$ts}""").withdrawBid(u)
        }
        assertEquals("LOGYX-BID-005", e.code)
    }

    @Test fun `cancelar solicitud manda el motivo recortado y omite el vacio`() = runTest {
        val seen = Seen()
        api(body = request.replace("PUBLISHED", "CANCELLED"), seen = seen).cancelRequest(u, "  Ya no la necesito ")
        assertEquals("/api/v1/shipment-requests/$u/cancel", seen.path)
        assertTrue(seen.body.contains(""""reason":"Ya no la necesito""""), seen.body)
        val sin = Seen()
        api(body = request, seen = sin).cancelRequest(u, "   ")
        assertTrue(!sin.body.contains("reason") || sin.body.contains("null"), sin.body)
    }

    @Test fun `reabrir y republicar envian la fecha opcional`() = runTest {
        val seen = Seen()
        val reopened = api(body = request, seen = seen).reopenRequest(u, "2026-11-30")
        assertEquals("POST /api/v1/shipment-requests/$u/reopen", seen.method + " " + seen.path)
        assertTrue(seen.body.contains(""""pickupDate":"2026-11-30""""), seen.body)
        assertEquals(RequestStatus.PUBLISHED, reopened.status)
        api(body = request, seen = seen).republishRequest(u)
        assertEquals("/api/v1/shipment-requests/$u/republish", seen.path)
    }

    @Test fun `REQ-004 y AUC-003 llegan como ApiException con su codigo`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.Conflict, """{"error":{"code":"LOGYX-REQ-004","message":"Solo se republica una solicitud cancelada"},$ts}""").republishRequest(u)
        }
        assertEquals("LOGYX-REQ-004", e.code)
    }

    @Test fun `cancelar envio manda motivo y parsea los campos de cancelacion`() = runTest {
        val seen = Seen()
        val s = api(body = shipment, seen = seen).cancelShipment(u, " Se averió el camión ")
        assertEquals("POST /api/v1/shipments/$u/cancel", seen.method + " " + seen.path)
        assertTrue(seen.body.contains(""""reason":"Se averió el camión""""), seen.body)
        assertEquals(ShipmentStatus.CANCELLED, s.status)
        assertEquals("Se averió el camión", s.cancellationReason)
        assertEquals("2026-11-20", s.pickupDate)
    }

    @Test fun `cambiar vehiculo es PUT con vehicleId y motivo`() = runTest {
        val seen = Seen()
        api(body = shipment.replace("CANCELLED", "IN_TRANSIT"), seen = seen).changeVehicle(u, "veh-1", "Falla mecánica")
        assertEquals("PUT /api/v1/shipments/$u/vehicle", seen.method + " " + seen.path)
        assertTrue(seen.body.contains(""""vehicleId":"veh-1"""") && seen.body.contains(""""reason":"Falla mecánica""""), seen.body)
    }

    @Test fun `CAP-003 llega con el campo vehicleId`() = runTest {
        val e = assertFailsWith<ApiException> {
            api(HttpStatusCode.BadRequest, """{"error":{"code":"LOGYX-CAP-003","message":"El tipo de vehículo no sirve","field":"vehicleId"},$ts}""").changeVehicle(u, "v")
        }
        assertEquals("vehicleId", e.field)
    }

    @Test fun `relevo de conductor manda reason en el PUT`() = runTest {
        val seen = Seen()
        api(body = shipment.replace("CANCELLED", "IN_TRANSIT"), seen = seen).assignDriver(u, "drv-1", "El conductor se enfermó")
        assertEquals("PUT /api/v1/shipments/$u/driver", seen.method + " " + seen.path)
        assertTrue(seen.body.contains(""""driverId":"drv-1"""") && seen.body.contains(""""reason":"El conductor se enfermó""""), seen.body)
    }

    @Test fun `aceptar smart load manda montos por carga y parsea las ofertas creadas`() = runTest {
        val seen = Seen()
        val body = """{"data":{"id":"$u","status":"ACCEPTED","items":[],"bids":[{"requestId":"r1","bidId":"b1","amount":8000.5},{"requestId":"r2","bidId":"b2","amount":6000}]},$ts}"""
        val plan = api(body = body, seen = seen).acceptSmartLoad(u, AcceptSmartLoadRequest(listOf(SmartLoadItemAmount("r1", 8000.5), SmartLoadItemAmount("r2", 6000.0)), "Salgo el lunes"))
        assertTrue(seen.body.contains(""""requestId":"r1"""") && seen.body.contains(""""notes":"Salgo el lunes""""), seen.body)
        assertEquals(2, plan.bids.size)
        assertEquals("b1", plan.bids.first().bidId)
        assertEquals(8000.5, plan.bids.first().amount)
    }

    @Test fun `TRP-011 y TRP-012 de smart load en lote`() = runTest {
        for (code in listOf("LOGYX-TRP-011", "LOGYX-TRP-012")) {
            val e = assertFailsWith<ApiException> {
                api(HttpStatusCode.Conflict, """{"error":{"code":"$code","message":"x","field":"items"},$ts}""").acceptSmartLoad(u)
            }
            assertEquals(code, e.code)
        }
    }
}
