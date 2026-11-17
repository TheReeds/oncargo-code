package pe.logyx.pricing.infrastructure.distance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.application.RoutePlanner;
import pe.logyx.routing.infrastructure.dataset.ClasspathRouteDataset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadDistanceProviderTest {

    private final RoadDistanceProvider provider = new RoadDistanceProvider(
            new RoutePlanner(new ClasspathRouteDataset(ClasspathRouteDataset.DEFAULT_RESOURCE, new ObjectMapper())));

    @Test
    void usaLaDistanciaPorCarreteraDelDataset() {
        // Por carretera (OSRM sobre OSM): Lima → Arequipa ≈ 1,002 km; la línea recta × 1.35 daba 1,034.
        assertEquals(1001.6, provider.roadDistanceKm(City.LIMA, City.AREQUIPA), 0.05);
        // Cusco → Abancay: la recta × 1.35 daba 134 km; la carretera son ≈ 208.
        assertEquals(208.1, provider.roadDistanceKm(City.CUSCO, City.ABANCAY), 0.05);
    }
}
