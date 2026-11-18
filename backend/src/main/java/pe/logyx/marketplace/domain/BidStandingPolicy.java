package pe.logyx.marketplace.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ofertas selladas (RN04): convierte la distancia a la mejor oferta en una banda que no revela montos. */
public final class BidStandingPolicy {

    private static final BigDecimal FIVE = new BigDecimal("0.05");
    private static final BigDecimal FIFTEEN = new BigDecimal("0.15");

    private BidStandingPolicy() {}

    /** @param mine monto de la oferta propia; @param best monto de la mejor oferta (el mínimo, no mayor que {@code mine}). */
    public static BidStandingBand band(BigDecimal mine, BigDecimal best) {
        if (mine.compareTo(best) <= 0) return BidStandingBand.BEST;
        BigDecimal over = mine.subtract(best).divide(best, 6, RoundingMode.HALF_UP);
        if (over.compareTo(FIVE) <= 0) return BidStandingBand.WITHIN_5_PCT;
        if (over.compareTo(FIFTEEN) <= 0) return BidStandingBand.WITHIN_15_PCT;
        return BidStandingBand.ABOVE_15_PCT;
    }
}
