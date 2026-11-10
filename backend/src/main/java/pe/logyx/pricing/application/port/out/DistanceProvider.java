package pe.logyx.pricing.application.port.out;

import pe.logyx.catalog.domain.City;

/** Distancia por carretera entre dos ciudades. Implementación actual: Haversine × factor vial. */
public interface DistanceProvider {
    double roadDistanceKm(City from, City to);
}
