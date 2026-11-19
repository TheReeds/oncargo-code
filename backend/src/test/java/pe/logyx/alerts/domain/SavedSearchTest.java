package pe.logyx.alerts.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.alerts.domain.SavedSearchMatcher.Load;
import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.alerts.domain.model.SavedSearch.Filters;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SavedSearchTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 12, 10);
    private static final Load LOAD = new Load(City.LIMA, City.JULIACA, CargoType.GENERAL, new BigDecimal("2000"), PICKUP);

    private static SavedSearch search(Filters f) {
        return SavedSearch.create(UUID.randomUUID(), UUID.randomUUID(), f, OffsetDateTime.now());
    }

    private static Filters filters(City o, City d, CargoType t, LocalDate from, LocalDate to, String kg) {
        return new Filters("Mi ruta", o, d, t, from, to, kg == null ? null : new BigDecimal(kg), null);
    }

    @Test
    void coincideCuandoTodosLosFiltrosPresentesCoinciden() {
        assertTrue(SavedSearchMatcher.matches(search(filters(City.LIMA, City.JULIACA, CargoType.GENERAL, null, null, null)), LOAD));
        assertTrue(SavedSearchMatcher.matches(search(filters(City.LIMA, null, null, null, null, null)), LOAD));
        assertTrue(SavedSearchMatcher.matches(search(filters(null, City.JULIACA, null, null, null, null)), LOAD));
        assertFalse(SavedSearchMatcher.matches(search(filters(City.LIMA, City.PUNO, null, null, null, null)), LOAD));
        assertFalse(SavedSearchMatcher.matches(search(filters(City.ICA, null, null, null, null, null)), LOAD));
        assertFalse(SavedSearchMatcher.matches(search(filters(null, null, CargoType.PERISHABLE, null, null, null)), LOAD));
    }

    @Test
    void fechasYPesoSonInclusivos() {
        assertTrue(SavedSearchMatcher.matches(search(filters(City.LIMA, null, null, PICKUP, PICKUP, "2000")), LOAD));
        assertFalse(SavedSearchMatcher.matches(search(filters(City.LIMA, null, null, PICKUP.plusDays(1), null, null)), LOAD));
        assertFalse(SavedSearchMatcher.matches(search(filters(City.LIMA, null, null, null, PICKUP.minusDays(1), null)), LOAD));
        assertFalse(SavedSearchMatcher.matches(search(filters(City.LIMA, null, null, null, null, "1999")), LOAD));
    }

    @Test
    void pausadaNoCoincide() {
        var s = search(filters(City.LIMA, null, null, null, null, null));
        s.setActive(false, OffsetDateTime.now());
        assertFalse(SavedSearchMatcher.matches(s, LOAD));
    }

    @Test
    void validaNombreYQueHayaAlgunFiltro() {
        assertEquals("LOGYX-ALT-001", assertThrows(BusinessException.class,
                () -> search(new Filters(" ", City.LIMA, null, null, null, null, null, null))).code());
        assertEquals("LOGYX-ALT-001", assertThrows(BusinessException.class,
                () -> search(new Filters("Sin filtros", null, null, null, null, null, null, null))).code());
        assertEquals("LOGYX-ALT-001", assertThrows(BusinessException.class,
                () -> search(new Filters("x".repeat(81), City.LIMA, null, null, null, null, null, null))).code());
        assertEquals("LOGYX-ALT-003", assertThrows(BusinessException.class,
                () -> search(filters(City.LIMA, City.LIMA, null, null, null, null))).code());
        assertEquals("LOGYX-ALT-003", assertThrows(BusinessException.class,
                () -> search(filters(City.LIMA, null, null, PICKUP.plusDays(2), PICKUP, null))).code());
        assertDoesNotThrow(() -> search(new Filters("Solo vehículo", null, null, null, null, null, null, UUID.randomUUID())));
    }

    @Test
    void limitePorHora() {
        assertTrue(SavedSearchMatcher.withinHourlyLimit(9, 10));
        assertFalse(SavedSearchMatcher.withinHourlyLimit(10, 10));
    }
}
