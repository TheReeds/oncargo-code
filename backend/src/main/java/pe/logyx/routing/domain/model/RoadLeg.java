package pe.logyx.routing.domain.model;

import pe.logyx.catalog.domain.City;

/**
 * Tramo por carretera entre dos ciudades del catálogo (ruta más rápida del dataset, ADR-0008).
 * {@code estimated = true} si el par no está en el dataset y se usó la estimación en línea recta.
 */
public record RoadLeg(City from, City to, double distanceKm, double durationMin, boolean estimated) {

    public static RoadLeg zero(City city) {
        return new RoadLeg(city, city, 0, 0, false);
    }
}
