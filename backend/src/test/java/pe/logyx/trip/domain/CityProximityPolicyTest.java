package pe.logyx.trip.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.trip.domain.Roads.PROXIMITY;

class CityProximityPolicyTest {

    @Test
    void mismaCiudadEsCero() {
        assertEquals(0, PROXIMITY.distanceKm(City.LIMA, City.LIMA));
    }

    @Test
    void juliacaYPunoEstanCercaEnAmbosSentidos() {
        // Por carretera (ADR-0008): 42.9 km Juliaca → Puno, 42.8 km Puno → Juliaca.
        assertEquals(42.9, PROXIMITY.distanceKm(City.JULIACA, City.PUNO), 0.05);
        assertEquals(42.8, PROXIMITY.distanceKm(City.PUNO, City.JULIACA), 0.05);
        assertTrue(PROXIMITY.within(City.PUNO, City.JULIACA, CityProximityPolicy.RETURN_RADIUS_KM));
    }

    @Test
    void restoDelCorredorEstaLejos() {
        assertFalse(PROXIMITY.within(City.LIMA, City.ICA, CityProximityPolicy.SMART_DESTINATION_RADIUS_KM));
        assertFalse(PROXIMITY.within(City.AREQUIPA, City.JULIACA, CityProximityPolicy.RETURN_RADIUS_KM));
        assertTrue(PROXIMITY.distanceKm(City.LIMA, City.AREQUIPA) > 900);
    }

    @Test
    void withinUsaElSentidoMasCorto() {
        CityProximityPolicy asym = new CityProximityPolicy((a, b) -> a == City.LIMA ? 100 : 40);
        assertTrue(asym.within(City.LIMA, City.ICA, 50));
        assertTrue(asym.within(City.ICA, City.LIMA, 50));
        assertEquals(100, asym.distanceKm(City.LIMA, City.ICA));
    }
}
