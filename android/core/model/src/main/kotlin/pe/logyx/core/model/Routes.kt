package pe.logyx.core.model

import kotlinx.serialization.Serializable

// --- Rutas por carretera (ADR-0008, GET /api/v1/routes) ---

/** Tramo entre dos paradas consecutivas. `estimated`: sin dato vial (línea recta × 1.35). */
@Serializable
data class RouteLegView(
    val fromCity: String,
    val toCity: String,
    val distanceKm: Double,
    val durationMin: Double,
    val estimated: Boolean = false,
)

/**
 * Ruta por carretera entre ciudades del catálogo. `polyline`: algoritmo de Google con precisión `polylinePrecision`.
 * `attribution` (© OpenStreetMap, ODbL) debe mostrarse junto al mapa.
 */
@Serializable
data class RouteView(
    val stops: List<String> = emptyList(),
    val distanceKm: Double = 0.0,
    val durationMin: Double = 0.0,
    val estimated: Boolean = false,
    val legs: List<RouteLegView> = emptyList(),
    val polyline: String = "",
    val polylinePrecision: Int = 5,
    val attribution: String = "",
)

/** Geometría lista para el mapa: puntos decodificados en orden de viaje. */
data class RouteGeometry(
    val points: List<GeoPoint>,
    val distanceKm: Double,
    val durationMin: Double,
    val attribution: String,
)

/** Decodifica la polyline; null si viene vacía o corrupta (el mapa cae a la línea recta). */
fun RouteView.toGeometry(): RouteGeometry? {
    val points = runCatching { Polyline.decode(polyline, polylinePrecision) }.getOrNull() ?: return null
    if (points.size < 2) return null
    return RouteGeometry(points, distanceKm, durationMin, attribution)
}

/** Polyline codificada (algoritmo de Google), igual que `routing/domain/PolylineCodec` del backend. */
object Polyline {

    fun decode(encoded: String, precision: Int = 5): List<GeoPoint> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<GeoPoint>()
        var index = 0
        var lat = 0L
        var lng = 0L
        while (index < encoded.length) {
            val delta = LongArray(2)
            for (k in 0..1) {
                var result = 0L
                var shift = 0
                var b: Int
                do {
                    require(index < encoded.length) { "Polyline truncada" }
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f).toLong() shl shift)
                    shift += 5
                } while (b >= 0x20)
                delta[k] = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            }
            lat += delta[0]
            lng += delta[1]
            out += GeoPoint(lat / factor, lng / factor)
        }
        return out
    }

    fun encode(points: List<GeoPoint>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        fun write(delta: Long) {
            var v = if (delta < 0) (delta shl 1).inv() else delta shl 1
            while (v >= 0x20) {
                sb.append(((0x20L or (v and 0x1f)) + 63).toInt().toChar())
                v = v shr 5
            }
            sb.append((v + 63).toInt().toChar())
        }
        for (p in points) {
            val la = Math.round(p.lat * factor)
            val ln = Math.round(p.lng * factor)
            write(la - prevLat)
            write(ln - prevLng)
            prevLat = la
            prevLng = ln
        }
        return sb.toString()
    }
}
