package pe.logyx.routing.domain;

import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.model.RoadLeg;

import java.util.Optional;

/** Distancias y tiempos por carretera conocidos entre ciudades del catálogo (dirigido: A→B puede diferir de B→A). */
public interface RoadNetwork {

    Optional<RoadLeg> leg(City from, City to);
}
