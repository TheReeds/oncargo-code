package pe.logyx.identity.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.domain.model.OrganizationPreferences.InterestRoute;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationPreferencesPolicyTest {

    private static final UUID ORG = UUID.randomUUID();

    private static BusinessException fails(OrgType t, List<InterestRoute> routes, BigDecimal price, LocalTime from,
                                           LocalTime to, Integer terms) {
        return assertThrows(BusinessException.class, () -> OrganizationPreferencesPolicy.normalize(ORG, t, List.of(), routes,
                List.of(), null, price, from, to, terms, null, OffsetDateTime.now()));
    }

    @Test
    void transportistaGuardaRutasTiposYPrecioYDescartaLoDeLaPyme() {
        var p = OrganizationPreferencesPolicy.normalize(ORG, OrgType.CARRIER, List.of(City.LIMA, City.LIMA, City.PUNO),
                List.of(new InterestRoute(City.LIMA, City.JULIACA), new InterestRoute(City.LIMA, City.JULIACA)),
                List.of(CargoType.GENERAL), false, new BigDecimal("3.456"), LocalTime.of(8, 0), LocalTime.of(12, 0), 30,
                null, OffsetDateTime.now());
        assertEquals(List.of(City.LIMA, City.PUNO), p.cities(), "sin repetidos y en orden");
        assertEquals(1, p.routes().size());
        assertFalse(p.routeAlerts());
        assertEquals(new BigDecimal("3.46"), p.minPricePerKm());
        assertNull(p.defaultPickupFrom(), "el horario de recojo es de la PYME");
        assertNull(p.paymentTermsDays());
    }

    @Test
    void pymeGuardaHorarioYPlazoYDescartaLoDelTransportista() {
        var p = OrganizationPreferencesPolicy.normalize(ORG, OrgType.PYME, null, null, null, false, new BigDecimal("5"),
                LocalTime.of(8, 0), LocalTime.of(12, 0), 0, null, OffsetDateTime.now());
        assertEquals(LocalTime.of(8, 0), p.defaultPickupFrom());
        assertEquals(0, p.paymentTermsDays());
        assertNull(p.minPricePerKm());
        assertTrue(p.routeAlerts(), "no aplica a la PYME: queda en su valor por omisión");
        assertTrue(p.routes().isEmpty());
    }

    @Test
    void validaRutasHorarioPlazoYPrecio() {
        assertEquals("LOGYX-ORG-010", fails(OrgType.CARRIER, List.of(new InterestRoute(City.LIMA, City.LIMA)), null, null,
                null, null).code());
        List<InterestRoute> many = new ArrayList<>();
        for (City a : City.values()) for (City b : City.values()) if (a != b && many.size() < 21) many.add(new InterestRoute(a, b));
        var tooMany = fails(OrgType.CARRIER, many, null, null, null, null);
        assertEquals("LOGYX-ORG-011", tooMany.code());
        assertEquals("routes", tooMany.field());
        assertDoesNotThrow(() -> OrganizationPreferencesPolicy.normalize(ORG, OrgType.CARRIER, null, many.subList(0, 20),
                null, true, null, null, null, null, null, OffsetDateTime.now()));
        assertEquals("LOGYX-ORG-012", fails(OrgType.PYME, null, null, LocalTime.of(8, 0), null, null).code());
        assertEquals("LOGYX-ORG-012", fails(OrgType.PYME, null, null, LocalTime.of(12, 0), LocalTime.of(8, 0), null).code());
        assertEquals("LOGYX-ORG-013", fails(OrgType.PYME, null, null, null, null, 121).code());
        assertEquals("LOGYX-ORG-013", fails(OrgType.PYME, null, null, null, null, -1).code());
        assertEquals("LOGYX-ORG-014", fails(OrgType.CARRIER, null, BigDecimal.ZERO, null, null, null).code());
        assertEquals("LOGYX-ORG-014", fails(OrgType.CARRIER, null, new BigDecimal("100.01"), null, null, null).code());
    }
}
