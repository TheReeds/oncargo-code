package pe.logyx.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.toGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** `GET /api/v1/routes` (ADR-0008) contra `RouteView` y la decodificación de la polyline. */
class RoutesParsingTest {
    private val ts = """"meta":{"timestamp":"2026-11-16T20:00:00Z"}"""

    private fun api(status: HttpStatusCode, body: String, onUrl: (path: String, stops: String?) -> Unit = { _, _ -> }): RoutesApi {
        val engine = MockEngine { req ->
            onUrl(req.url.encodedPath, req.url.parameters["stops"])
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine))
    }

    @Test fun `ruta con tramos, polyline y atribucion`() = runTest {
        var path = ""
        var stops: String? = null
        val json = """{"data":{"stops":["AREQUIPA","PUNO"],"distanceKm":273.8,"durationMin":330.2,"estimated":false,
            "legs":[{"fromCity":"AREQUIPA","toCity":"PUNO","distanceKm":273.8,"durationMin":330.2,"estimated":false}],
            "polyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@","polylinePrecision":5,
            "attribution":"© OpenStreetMap contributors (ODbL 1.0, https://www.openstreetmap.org/copyright)"},$ts}"""
        val r = api(HttpStatusCode.OK, json) { p, st -> path = p; stops = st }.route(listOf("AREQUIPA", "PUNO"))
        assertEquals("/api/v1/routes", path)
        assertEquals("AREQUIPA,PUNO", stops)
        assertEquals(273.8, r.distanceKm)
        assertEquals("PUNO", r.legs.single().toCity)
        assertFalse(r.estimated)
        val g = r.toGeometry()!!
        assertEquals(3, g.points.size)
        assertEquals(38.5, g.points.first().lat)
    }

    @Test fun `error de paradas llega como ApiException`() = runTest {
        val err = """{"error":{"code":"LOGYX-RTE-001","message":"Indica al menos dos paradas (origen y destino)","field":"stops"},$ts}"""
        val e = assertFailsWith<ApiException> { api(HttpStatusCode.BadRequest, err).route(listOf("LIMA")) }
        assertEquals("LOGYX-RTE-001", e.code)
        assertEquals("stops", e.field)
    }
}
