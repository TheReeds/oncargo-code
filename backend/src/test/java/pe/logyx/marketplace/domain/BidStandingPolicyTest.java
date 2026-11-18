package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static pe.logyx.marketplace.domain.BidStandingBand.*;

class BidStandingPolicyTest {

    private static BidStandingBand band(String mine, String best) {
        return BidStandingPolicy.band(new BigDecimal(mine), new BigDecimal(best));
    }

    @Test
    void bandasPorDistanciaALaMejorOferta() {
        assertEquals(BEST, band("1000.00", "1000.00"));
        assertEquals(WITHIN_5_PCT, band("1000.01", "1000.00"));
        assertEquals(WITHIN_5_PCT, band("1050.00", "1000.00"));      // 5 % exacto: dentro
        assertEquals(WITHIN_15_PCT, band("1050.01", "1000.00"));
        assertEquals(WITHIN_15_PCT, band("1150.00", "1000.00"));     // 15 % exacto: dentro
        assertEquals(ABOVE_15_PCT, band("1150.01", "1000.00"));
        assertEquals(ABOVE_15_PCT, band("5000.00", "1000.00"));
    }

    @Test
    void unaOfertaMenorQueLaMejorNuncaSeDegrada() {
        assertEquals(BEST, band("900.00", "1000.00"));
    }
}
