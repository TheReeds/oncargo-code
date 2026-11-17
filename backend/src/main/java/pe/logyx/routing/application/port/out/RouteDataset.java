package pe.logyx.routing.application.port.out;

import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.RoadNetwork;
import pe.logyx.routing.domain.model.LatLng;

import java.util.List;
import java.util.Optional;

/**
 * Fuente de datos viales (ADR-0008). Hoy: dataset precalculado en el classpath ({@code routing/routes.json}).
 * Mañana: un motor propio (OSRM/Valhalla) para direcciones arbitrarias, como otra implementación de este puerto.
 */
public interface RouteDataset {

    RoadNetwork network();

    /** Geometría de la ruta {@code from → to} (lat/lng en orden de viaje), si se conoce. */
    Optional<List<LatLng>> geometry(City from, City to);

    /** Atribución obligatoria de los datos (ODbL de OpenStreetMap) para mostrar junto al mapa. */
    String attribution();
}
