package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.application.MarketplaceFilter;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceFilterTest {

    @Test
    void sinParametrosOrdenaPorRecojo() {
        var f = MarketplaceFilter.of(null, null, null, null, null, null, null);
        assertEquals(MarketplaceFilter.Sort.PICKUP, f.sort());
        assertNull(f.originCity());
    }

    @Test
    void parseaCiudadesTipoYOrden() {
        var f = MarketplaceFilter.of("lima", "JULIACA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                new BigDecimal("5000"), "perishable", "price_desc");
        assertEquals(City.LIMA, f.originCity());
        assertEquals(CargoType.PERISHABLE, f.cargoType());
        assertEquals(MarketplaceFilter.Sort.PRICE_DESC, f.sort());
    }

    @Test
    void parametrosInvalidosDan400ConField() {
        assertEquals("sort", fieldOf(() -> MarketplaceFilter.of(null, null, null, null, null, null, "x")));
        assertEquals("originCity", fieldOf(() -> MarketplaceFilter.of("MARTE", null, null, null, null, null, null)));
        assertEquals("cargoType", fieldOf(() -> MarketplaceFilter.of(null, null, null, null, null, "X", null)));
        assertEquals("maxWeightKg", fieldOf(() -> MarketplaceFilter.of(null, null, null, null, BigDecimal.ZERO, null, null)));
        assertEquals("pickupFrom", fieldOf(() -> MarketplaceFilter.of(null, null,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1), null, null, null)));
    }

    private static String fieldOf(Runnable r) {
        return assertThrows(BusinessException.class, r::run).field();
    }
}
