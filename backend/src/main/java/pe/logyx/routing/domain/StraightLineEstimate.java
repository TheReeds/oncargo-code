package pe.logyx.routing.domain;

import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.model.RoadLeg;

/**
 * Respaldo cuando un par no está en el dataset (p. ej. ciudad nueva sin regenerar): línea recta × 1.35 (el cálculo
 * anterior a ADR-0008) a 50 km/h. El tramo queda marcado {@code estimated}.
 */
public final class StraightLineEstimate {

    public static final double ROAD_FACTOR = 1.35;
    public static final double AVERAGE_KMH = 50;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private StraightLineEstimate() {}

    public static RoadLeg leg(City from, City to) {
        if (from == to) return RoadLeg.zero(from);
        double km = haversineKm(from.lat, from.lng, to.lat, to.lng) * ROAD_FACTOR;
        return new RoadLeg(from, to, km, km / AVERAGE_KMH * 60, true);
    }

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
