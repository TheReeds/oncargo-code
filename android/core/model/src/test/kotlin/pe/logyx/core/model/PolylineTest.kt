package pe.logyx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolylineTest {

    private val sample = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun decodificaElEjemploDeReferencia() {
        val pts = Polyline.decode(sample)
        assertEquals(listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453)), pts)
        assertEquals(sample, Polyline.encode(pts))
    }

    @Test
    fun rutaSinPolylineOCorruptaNoTieneGeometria() {
        assertNull(RouteView(polyline = "").toGeometry())
        assertNull(RouteView(polyline = "_p~iF~ps|U_").toGeometry())
        val g = RouteView(polyline = sample, distanceKm = 12.5, attribution = "© OpenStreetMap").toGeometry()!!
        assertEquals(3, g.points.size)
        assertEquals("© OpenStreetMap", g.attribution)
    }
}
