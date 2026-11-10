package pe.logyx.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * RN03: ninguna oferta puede ser menor al {@code ratio} (75% por defecto) del
 * precio sugerido por el motor de costos. Aquí se calcula el piso; el
 * marketplace lo guarda en la solicitud y lo hace cumplir al ofertar
 * ({@code marketplace.domain.BidEligibilityPolicy}).
 */
public final class PriceFloorPolicy {

    private final BigDecimal ratio;

    public PriceFloorPolicy(BigDecimal ratio) {
        Objects.requireNonNull(ratio, "ratio");
        if (ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("El ratio del piso debe estar en (0, 1]: " + ratio);
        }
        this.ratio = ratio;
    }

    public BigDecimal ratio() {
        return ratio;
    }

    public BigDecimal floorFor(BigDecimal suggestedPrice) {
        return suggestedPrice.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean accepts(BigDecimal amount, BigDecimal floor) {
        return amount.compareTo(floor) >= 0;
    }
}
