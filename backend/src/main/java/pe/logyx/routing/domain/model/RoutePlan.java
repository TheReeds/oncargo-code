package pe.logyx.routing.domain.model;

import pe.logyx.catalog.domain.City;

import java.util.List;

/** Recorrido por una secuencia de ciudades: tramos consecutivos y totales. */
public record RoutePlan(List<City> stops, List<RoadLeg> legs, double distanceKm, double durationMin) {

    public RoutePlan {
        stops = List.copyOf(stops);
        legs = List.copyOf(legs);
    }

    /** true si algún tramo salió de la estimación en línea recta (dataset desactualizado). */
    public boolean estimated() {
        return legs.stream().anyMatch(RoadLeg::estimated);
    }
}
