package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** RN02 (vehículo disponible y con capacidad) y RN03 (piso de precio) al ofertar. */
class BidEligibilityPolicyTest {

    @Test
    void rechazaOfertaBajoElPiso() {
        var e = assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureAboveFloor(new BigDecimal("99.99"), new BigDecimal("100.00")));
        assertEquals("LOGYX-BID-001", e.code());
        assertDoesNotThrow(() -> BidEligibilityPolicy.ensureAboveFloor(new BigDecimal("100.00"), new BigDecimal("100.00")));
    }

    @Test
    void vehiculoNoDisponibleEsConflicto() {
        var e = assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureVehicleEligible(false, new BigDecimal("8000"), new BigDecimal("100")));
        assertEquals("LOGYX-CAP-002", e.code());
        assertEquals(409, e.status());
    }

    @Test
    void vehiculoSinCapacidadEsRechazado() {
        var e = assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureVehicleEligible(true, new BigDecimal("1000"), new BigDecimal("1000.5")));
        assertEquals("LOGYX-CAP-001", e.code());
        assertDoesNotThrow(() -> BidEligibilityPolicy.ensureVehicleEligible(true, new BigDecimal("1000"), new BigDecimal("1000")));
    }
}
