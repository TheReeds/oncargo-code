package pe.logyx.pricing;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.catalog.City;

/**
 * Aproximación sin red: distancia geodésica × 1.35 (sinuosidad media de la
 * Panamericana Sur y las vías a la sierra). Se reemplaza por OpenRouteService
 * (ADR-05) agregando otra implementación de {@link DistanceProvider}.
 */
@ApplicationScoped
public class HaversineDistanceProvider implements DistanceProvider {

    static final double ROAD_FACTOR = 1.35;
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public double roadDistanceKm(City from, City to) {
        double dLat = Math.toRadians(to.lat - from.lat);
        double dLng = Math.toRadians(to.lng - from.lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.lat)) * Math.cos(Math.toRadians(to.lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double straight = 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return straight * ROAD_FACTOR;
    }
}
