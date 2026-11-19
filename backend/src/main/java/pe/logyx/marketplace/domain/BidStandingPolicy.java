package pe.logyx.marketplace.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ofertas selladas (RN04): convierte la distancia a la mejor oferta en una banda que no revela montos. */
public final class BidStandingPolicy {

    private static final BigDecimal FIVE = new BigDecimal("0.05");
    private static final BigDecimal FIFTEEN = new BigDecimal("0.15");

    private BidStandingPolicy() {}

    /**
     * Posición aproximada: rank 1 = la más baja. Las 2 más bajas (con 2 ofertas o menos, todas) son {@code TOP_TWO};
     * después, la mediana o menos es {@code LOWER_HALF} y lo que queda por encima de la mediana, {@code UPPER_HALF}
     * ({@code 2·rank > bidCount + 1}).
     */
    public static BidPosition position(int rank, int bidCount) {
        if (rank <= 2) return BidPosition.TOP_TWO;
        return 2 * rank > bidCount + 1 ? BidPosition.UPPER_HALF : BidPosition.LOWER_HALF;
    }

    /** @param mine monto de la oferta propia; @param best monto de la mejor oferta (el mínimo, no mayor que {@code mine}). */
    public static BidStandingBand band(BigDecimal mine, BigDecimal best) {
        if (mine.compareTo(best) <= 0) return BidStandingBand.BEST;
        BigDecimal over = mine.subtract(best).divide(best, 6, RoundingMode.HALF_UP);
        if (over.compareTo(FIVE) <= 0) return BidStandingBand.WITHIN_5_PCT;
        if (over.compareTo(FIFTEEN) <= 0) return BidStandingBand.WITHIN_15_PCT;
        return BidStandingBand.ABOVE_15_PCT;
    }
}
