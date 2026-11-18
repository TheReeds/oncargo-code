package pe.logyx.core.data

import kotlinx.coroutines.test.runTest
import org.junit.Test
import pe.logyx.core.model.RouteView
import pe.logyx.core.network.ApiException
import pe.logyx.core.network.RoutesApi
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutesRepositoryTest {

    private class FakeApi(var fail: Boolean = false) : RoutesApi {
        val calls = mutableListOf<List<String>>()
        override suspend fun route(stops: List<String>, optimize: Boolean): RouteView {
            calls += stops
            if (fail) throw ApiException(0, "NETWORK", "sin red")
            return RouteView(stops = stops, distanceKm = 43.0, polyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@", attribution = "© OpenStreetMap")
        }
    }

    @Test fun `normaliza las ciudades, cachea y decodifica`() = runTest {
        val api = FakeApi()
        val repo = DefaultRoutesRepository(api)
        val g = repo.between("Juliaca", "PUNO")!!
        assertEquals(3, g.points.size)
        repo.route(listOf("JULIACA", "Puno"))
        assertEquals(listOf(listOf("JULIACA", "PUNO")), api.calls, "la segunda vez sale de la caché")
    }

    @Test fun `sin red o con una sola ciudad no hay ruta`() = runTest {
        val repo = DefaultRoutesRepository(FakeApi(fail = true))
        assertNull(repo.between("LIMA", "CUSCO"))
        assertTrue(repo.route(listOf("LIMA", "LIMA")).isFailure)
        assertNull(repo.between(null, "CUSCO"))
        assertTrue(RoutesRepository.NONE.route(listOf("LIMA", "CUSCO")).isFailure)
    }
}
