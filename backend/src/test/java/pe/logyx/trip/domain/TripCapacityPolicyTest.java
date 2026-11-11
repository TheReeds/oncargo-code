package pe.logyx.trip.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** RN12: capacidad publicada ≤ capacidad del vehículo; las reservas confirmadas descuentan la restante. */
class TripCapacityPolicyTest {

    private static BigDecimal kg(String v) { return new BigDecimal(v); }

    @Test
    void capacidadIgualOMenorAlVehiculoEsPublicable() {
        assertDoesNotThrow(() -> TripCapacityPolicy.ensurePublishable(kg("8000"), kg("8000")));
        assertDoesNotThrow(() -> TripCapacityPolicy.ensurePublishable(kg("500"), kg("8000")));
    }

    @Test
    void capacidadMayorAlVehiculoSeRechazaConCodigoYCampo() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> TripCapacityPolicy.ensurePublishable(kg("8000.01"), kg("8000")));
        assertEquals("LOGYX-TRP-001", e.code());
        assertEquals("capacityKg", e.field());
        assertEquals(400, e.status());
    }

    @Test
    void lasReservasConfirmadasDescuentanLaCapacidadRestante() {
        assertEquals(0, kg("5000").compareTo(TripCapacityPolicy.remaining(kg("8000"), kg("3000"))));
        assertEquals(0, kg("0").compareTo(TripCapacityPolicy.remaining(kg("8000"), kg("8000"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(TripCapacityPolicy.remaining(kg("100"), kg("150"))), "nunca negativa");
    }

    @Test
    void elPesoDebeCaberEnLoQueQueda() {
        assertDoesNotThrow(() -> TripCapacityPolicy.ensureFits(kg("5000"), kg("5000")));
        BusinessException e = assertThrows(BusinessException.class,
                () -> TripCapacityPolicy.ensureFits(kg("5000.5"), kg("5000")));
        assertEquals("LOGYX-TRP-004", e.code());
        assertEquals("weightKg", e.field());
        assertEquals(409, e.status());
    }
}
