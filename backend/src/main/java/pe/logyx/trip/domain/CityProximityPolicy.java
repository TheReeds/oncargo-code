package pe.logyx.trip.domain;

import pe.logyx.catalog.domain.City;

import java.util.Objects;

/**
 * Proximidad entre ciudades del corredor para cargas de retorno (RF61) y Smart Load (RF30), con distancias por
 * carretera ({@link RoadDistances}, ADR-0008). Las rutas pueden diferir por sentido: {@link #distanceKm} es dirigida
 * (de dónde queda el vehículo a dónde recoge) y {@link #within} usa el menor de los dos sentidos (separación entre
 * dos ciudades, simétrica para agrupar cargas).
 */
public final class CityProximityPolicy {

    /** Radio para considerar "cercano" un origen respecto al destino recién entregado (RF61 pide 30 km; el corredor solo tiene Juliaca–Puno a 43 km por carretera). */
    public static final double RETURN_RADIUS_KM = 50;
    /** RF30: orígenes similares dentro de 50 km. */
    public static final double SMART_ORIGIN_RADIUS_KM = 50;
    /** RF30: destinos similares dentro de 80 km. */
    public static final double SMART_DESTINATION_RADIUS_KM = 80;

    private final RoadDistances roads;

    public CityProximityPolicy(RoadDistances roads) {
        this.roads = Objects.requireNonNull(roads);
    }

    /** Km por carretera de {@code from} a {@code to} (0 si es la misma ciudad). */
    public double distanceKm(City from, City to) {
        return from == to ? 0 : roads.km(from, to);
    }

    /** true si las dos ciudades están a ≤ {@code radiusKm} por carretera (en el sentido más corto). */
    public boolean within(City a, City b, double radiusKm) {
        return Math.min(distanceKm(a, b), distanceKm(b, a)) <= radiusKm;
    }
}
