package pe.logyx.core.network

import pe.logyx.core.model.RouteView

/** Rutas por carretera (ADR-0008): `GET /api/v1/routes?stops=A,B[,C…][&optimize=true]`. Implementado por [LogyxApi]. */
interface RoutesApi {
    /** [stops]: códigos de ciudad del catálogo (`LIMA`, `AREQUIPA`…) en orden de visita; al menos dos. */
    suspend fun route(stops: List<String>, optimize: Boolean = false): RouteView
}
