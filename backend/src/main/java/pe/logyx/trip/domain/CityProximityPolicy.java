package pe.logyx.trip.domain;

import pe.logyx.catalog.domain.City;

import java.util.Map;
import java.util.Set;

/**
 * Proximidad entre ciudades del corredor, para cargas de retorno (RF61) y Smart Load (RF30). Tabla simple de
 * distancias por carretera de pares cercanos; para el resto se usa la línea recta × 1.35 (igual que el motor de costos).
 */
public final class CityProximityPolicy {

    /** Radio para considerar "cercano" un origen respecto al destino recién entregado (RF61 pide 30 km; el corredor solo tiene Juliaca–Puno a 44 km). */
    public static final double RETURN_RADIUS_KM = 50;
    /** RF30: orígenes similares dentro de 50 km. */
    public static final double SMART_ORIGIN_RADIUS_KM = 50;
    /** RF30: destinos similares dentro de 80 km. */
    public static final double SMART_DESTINATION_RADIUS_KM = 80;

    private static final double ROAD_FACTOR = 1.35;
    private static final Map<Set<City>, Double> KNOWN_ROAD_KM = Map.of(
            Set.of(City.JULIACA, City.PUNO), 44.0);

    private CityProximityPolicy() {}

    public static double distanceKm(City a, City b) {
        if (a == b) return 0;
        Double known = KNOWN_ROAD_KM.get(Set.of(a, b));
        return known != null ? known : haversineKm(a, b) * ROAD_FACTOR;
    }

    public static boolean within(City a, City b, double radiusKm) {
        return distanceKm(a, b) <= radiusKm;
    }

    private static double haversineKm(City a, City b) {
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLng = Math.toRadians(b.lng - a.lng);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.pow(Math.sin(dLng / 2), 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
