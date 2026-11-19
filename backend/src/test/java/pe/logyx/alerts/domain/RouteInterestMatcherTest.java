package pe.logyx.alerts.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.alerts.domain.RouteInterestMatcher.Interest;
import pe.logyx.alerts.domain.RouteInterestMatcher.Load;
import pe.logyx.alerts.domain.RouteInterestMatcher.Route;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouteInterestMatcherTest {

    private static final Load LIMA_JULIACA = new Load(City.LIMA, City.JULIACA, CargoType.GENERAL, new BigDecimal("3000"),
            new BigDecimal("1200"));

    @Test
    void coincideSoloConOrigenYDestinoDeUnaRuta() {
        var i = new Interest(Set.of(new Route(City.LIMA, City.JULIACA)), Set.of(), null);
        assertTrue(RouteInterestMatcher.matches(i, LIMA_JULIACA));
        assertFalse(RouteInterestMatcher.matches(i, new Load(City.JULIACA, City.LIMA, CargoType.GENERAL, null, null)),
                "la ruta tiene sentido");
        assertFalse(RouteInterestMatcher.matches(new Interest(Set.of(), Set.of(), null), LIMA_JULIACA));
    }

    @Test
    void respetaTiposDeCargaYPrecioMinimoPorKm() {
        var route = Set.of(new Route(City.LIMA, City.JULIACA));
        assertFalse(RouteInterestMatcher.matches(new Interest(route, Set.of(CargoType.PERISHABLE), null), LIMA_JULIACA));
        assertTrue(RouteInterestMatcher.matches(new Interest(route, Set.of(CargoType.GENERAL, CargoType.BULK), null), LIMA_JULIACA));
        assertEquals(new BigDecimal("2.50"), RouteInterestMatcher.pricePerKm(LIMA_JULIACA));
        assertTrue(RouteInterestMatcher.matches(new Interest(route, Set.of(), new BigDecimal("2.50")), LIMA_JULIACA));
        assertFalse(RouteInterestMatcher.matches(new Interest(route, Set.of(), new BigDecimal("2.51")), LIMA_JULIACA));
        // Sin precio o km no se puede comprobar el mínimo: no avisa
        var unknown = new Load(City.LIMA, City.JULIACA, CargoType.GENERAL, null, new BigDecimal("1200"));
        assertFalse(RouteInterestMatcher.matches(new Interest(route, Set.of(), BigDecimal.ONE), unknown));
        assertTrue(RouteInterestMatcher.matches(new Interest(route, Set.of(), null), unknown));
        assertTrue(RouteInterestMatcher.withinHourlyLimit(19, 20));
        assertFalse(RouteInterestMatcher.withinHourlyLimit(20, 20));
    }
}
