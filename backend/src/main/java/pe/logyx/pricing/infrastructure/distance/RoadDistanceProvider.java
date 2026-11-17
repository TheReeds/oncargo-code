package pe.logyx.pricing.infrastructure.distance;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.catalog.domain.City;
import pe.logyx.pricing.application.port.out.DistanceProvider;
import pe.logyx.routing.application.RoutePlanner;

/**
 * Distancia por carretera del módulo {@code routing} (ADR-0008): ruta más rápida precalculada sobre OpenStreetMap.
 * Reemplaza a la línea recta × 1.35 (el módulo routing la conserva solo como respaldo marcado "estimado").
 */
@ApplicationScoped
public class RoadDistanceProvider implements DistanceProvider {

    private final RoutePlanner routes;

    @Inject
    public RoadDistanceProvider(RoutePlanner routes) {
        this.routes = routes;
    }

    @Override
    public double roadDistanceKm(City from, City to) {
        return routes.distanceKm(from, to);
    }
}
