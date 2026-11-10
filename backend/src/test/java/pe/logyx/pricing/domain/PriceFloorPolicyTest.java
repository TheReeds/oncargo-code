package pe.logyx.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** RN03: oferta ≥ 75% del precio sugerido. */
class PriceFloorPolicyTest {

    private final PriceFloorPolicy policy = new PriceFloorPolicy(new BigDecimal("0.75"));

    @Test
    void pisoEsEl75PorCientoRedondeadoAlCentimo() {
        assertEquals(new BigDecimal("750.00"), policy.floorFor(new BigDecimal("1000")));
        assertEquals(new BigDecimal("75.01"), policy.floorFor(new BigDecimal("100.01")));  // 75.0075 → 75.01
    }

    @Test
    void aceptaMontoIgualOSuperiorAlPiso() {
        assertTrue(policy.accepts(new BigDecimal("750.00"), new BigDecimal("750.00")));
        assertTrue(policy.accepts(new BigDecimal("800"), new BigDecimal("750.00")));
        assertFalse(policy.accepts(new BigDecimal("749.99"), new BigDecimal("750.00")));
    }

    @Test
    void rechazaRatiosFueraDeRango() {
        assertThrows(IllegalArgumentException.class, () -> new PriceFloorPolicy(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PriceFloorPolicy(new BigDecimal("1.2")));
    }
}
