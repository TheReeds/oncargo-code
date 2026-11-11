package pe.logyx.trip.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;

import static org.junit.jupiter.api.Assertions.*;

class CityProximityPolicyTest {

    @Test
    void mismaCiudadEsCero() {
        assertEquals(0, CityProximityPolicy.distanceKm(City.LIMA, City.LIMA));
    }

    @Test
    void juliacaYPunoEstanCercaEnAmbosSentidos() {
        assertEquals(44, CityProximityPolicy.distanceKm(City.JULIACA, City.PUNO));
        assertEquals(44, CityProximityPolicy.distanceKm(City.PUNO, City.JULIACA));
        assertTrue(CityProximityPolicy.within(City.PUNO, City.JULIACA, CityProximityPolicy.RETURN_RADIUS_KM));
    }

    @Test
    void restoDelCorredorEstaLejos() {
        assertFalse(CityProximityPolicy.within(City.LIMA, City.ICA, CityProximityPolicy.SMART_DESTINATION_RADIUS_KM));
        assertFalse(CityProximityPolicy.within(City.AREQUIPA, City.JULIACA, CityProximityPolicy.RETURN_RADIUS_KM));
        assertTrue(CityProximityPolicy.distanceKm(City.LIMA, City.AREQUIPA) > 700);
    }
}
