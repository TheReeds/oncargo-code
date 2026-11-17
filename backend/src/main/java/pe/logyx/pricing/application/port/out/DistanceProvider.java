package pe.logyx.pricing.application.port.out;

import pe.logyx.catalog.domain.City;

/** Distancia por carretera entre dos ciudades. Implementación: {@code RoadDistanceProvider} (módulo routing, ADR-0008). */
public interface DistanceProvider {
    double roadDistanceKm(City from, City to);
}
