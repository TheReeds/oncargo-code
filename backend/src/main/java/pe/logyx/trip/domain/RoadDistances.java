package pe.logyx.trip.domain;

import pe.logyx.catalog.domain.City;

/**
 * Distancia por carretera (km) de {@code from} a {@code to}. La provee {@code trip.application} con
 * {@code routing.application.RoutePlanner::distanceKm} (ADR-0008); el dominio de trip no conoce el módulo routing.
 */
@FunctionalInterface
public interface RoadDistances {

    double km(City from, City to);
}
