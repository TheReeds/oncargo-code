package pe.logyx.pricing;

import pe.logyx.catalog.City;

/** Distancia por carretera entre dos ciudades. Implementación actual: Haversine × factor vial. */
public interface DistanceProvider {
    double roadDistanceKm(City from, City to);
}
