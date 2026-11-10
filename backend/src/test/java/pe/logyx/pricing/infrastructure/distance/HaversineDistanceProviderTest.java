package pe.logyx.pricing.infrastructure.distance;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HaversineDistanceProviderTest {

    @Test
    void distanciaHaversineLimaArequipaEsRazonable() {
        double km = new HaversineDistanceProvider().roadDistanceKm(City.LIMA, City.AREQUIPA);
        // Por carretera son ~1,010 km; la aproximación debe quedar en ±15%.
        assertTrue(km > 860 && km < 1160, "km=" + km);
    }
}
