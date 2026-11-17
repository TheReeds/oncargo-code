package pe.logyx.trip.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import pe.logyx.routing.application.RoutePlanner;
import pe.logyx.routing.infrastructure.dataset.ClasspathRouteDataset;

/** Distancias por carretera reales (dataset de routing, ADR-0008) para los tests de dominio de trip. */
final class Roads {

    static final RoutePlanner PLANNER =
            new RoutePlanner(new ClasspathRouteDataset(ClasspathRouteDataset.DEFAULT_RESOURCE, new ObjectMapper()));
    static final CityProximityPolicy PROXIMITY = new CityProximityPolicy(PLANNER::distanceKm);

    private Roads() {}
}
