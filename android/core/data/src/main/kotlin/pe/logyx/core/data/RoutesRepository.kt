package pe.logyx.core.data

import pe.logyx.core.model.RouteGeometry
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeometry
import pe.logyx.core.network.RoutesApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Rutas por carretera para los mapas (ADR-0008). La ruta entre ciudades del catálogo es estática: se cachea en memoria.
 * Un fallo devuelve `Result.failure` y los mapas dibujan la línea recta de respaldo.
 */
interface RoutesRepository {
    /** [cities]: códigos o nombres de ciudad en orden de visita (se normalizan a `LIMA`, `AREQUIPA`…). */
    suspend fun route(cities: List<String>): Result<RouteGeometry>

    companion object {
        /** Sin rutas (tests y previews): siempre falla y el mapa usa la línea recta. */
        val NONE: RoutesRepository = object : RoutesRepository {
            override suspend fun route(cities: List<String>) = Result.failure<RouteGeometry>(IllegalStateException("sin rutas"))
        }
    }
}

class DefaultRoutesRepository(private val api: RoutesApi) : RoutesRepository {
    private val cache = ConcurrentHashMap<String, RouteGeometry>()

    override suspend fun route(cities: List<String>): Result<RouteGeometry> {
        val stops = cities.map(::normalizeCity).filter { it.isNotEmpty() }.fold(emptyList<String>()) { acc, c ->
            if (acc.lastOrNull() == c) acc else acc + c
        }
        if (stops.size < 2) return Result.failure(IllegalArgumentException("La ruta necesita origen y destino distintos"))
        val key = stops.joinToString(",")
        cache[key]?.let { return Result.success(it) }
        return apiCall { api.route(stops) }.mapCatching { view ->
            view.toGeometry() ?: throw IllegalStateException("Ruta sin geometría")
        }.onSuccess { cache[key] = it }
    }
}

/** Ruta origen → destino (null si no se pudo obtener: el mapa usa la línea recta). */
suspend fun RoutesRepository.between(origin: String?, destination: String?): RouteGeometry? =
    if (origin.isNullOrBlank() || destination.isNullOrBlank()) null else route(listOf(origin, destination)).getOrNull()
