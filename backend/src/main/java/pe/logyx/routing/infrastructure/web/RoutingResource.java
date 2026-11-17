package pe.logyx.routing.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.application.RoutePlanner;
import pe.logyx.routing.domain.model.RoutePlan;
import pe.logyx.routing.infrastructure.web.RoutingDtos.RouteView;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;

import java.util.Arrays;
import java.util.List;

/**
 * Ruta por carretera entre ciudades del catálogo (ADR-0008): km, minutos, tramos y geometría para dibujar el mapa.
 * Sin datos de organización: cualquier usuario autenticado (PYME, transportista, conductor, operador).
 */
@Path("/api/v1")
@Authenticated
public class RoutingResource {

    static final int MAX_STOPS = 25;

    @Inject RoutePlanner planner;

    /**
     * {@code stops}: códigos de ciudad separados por coma, en orden de visita (p. ej. {@code LIMA,AREQUIPA,PUNO}).
     * {@code optimize=true} reordena las paradas intermedias para el menor recorrido (la primera y la última no se mueven).
     */
    @GET
    @Path("/routes")
    public ApiResponse<RouteView> route(@QueryParam("stops") String stops,
                                        @QueryParam("optimize") @DefaultValue("false") boolean optimize) {
        List<City> cities = parse(stops);
        RoutePlan plan = optimize && cities.size() > 3
                ? planner.shortestTour(cities.get(0), cities.subList(1, cities.size() - 1), cities.get(cities.size() - 1))
                : planner.route(cities);
        return ApiResponse.of(RouteView.of(plan, planner.geometry(plan.stops()), planner.attribution()));
    }

    static List<City> parse(String stops) {
        List<String> codes = stops == null ? List.of()
                : Arrays.stream(stops.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (codes.size() < 2) {
            throw BusinessException.badRequest("LOGYX-RTE-001", "Indica al menos dos paradas (origen y destino)", "stops");
        }
        if (codes.size() > MAX_STOPS) {
            throw BusinessException.badRequest("LOGYX-RTE-002", "Una ruta admite como máximo " + MAX_STOPS + " paradas", "stops");
        }
        return codes.stream().map(City::require).toList();
    }
}
