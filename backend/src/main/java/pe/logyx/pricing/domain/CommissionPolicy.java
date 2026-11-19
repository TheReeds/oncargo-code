package pe.logyx.pricing.domain;

import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Comisión de OnCargo y desglose del IGV (decisión del dueño del producto, 2026-11-17). Dominio puro y sin estado:
 * lo reutilizan el {@code BidSheet} ("cobras S/ X neto"), la adjudicación (tasa vigente guardada en el envío), la
 * vista de la PYME (IGV desglosado) y los reportes.
 *
 * <ul>
 *   <li><b>Quién paga:</b> la comisión es un porcentaje que OnCargo retiene al <b>transportista</b>; la PYME paga el
 *       precio adjudicado, sin recargo.</li>
 *   <li><b>Base:</b> el precio adjudicado tal como se ofertó/aceptó (IGV incluido). {@code comisión = precio × tasa},
 *       2 decimales HALF_UP; {@code neto del transportista = precio − comisión}.</li>
 *   <li><b>IGV 18 %:</b> el precio adjudicado es final e incluye IGV; se desglosa
 *       {@code valor de venta = precio / 1.18} (2 decimales HALF_UP) e {@code IGV = precio − valor de venta}.</li>
 * </ul>
 * Supuestos pendientes de confirmar con el dueño: precio con IGV incluido y comisión sobre el total.
 */
public final class CommissionPolicy {

    /** IGV vigente en Perú. */
    public static final BigDecimal VAT_RATE = new BigDecimal("0.18");

    private CommissionPolicy() {}

    public static AwardedPriceBreakdown breakdown(BigDecimal amount, BigDecimal commissionRate) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(commissionRate, "commissionRate");
        if (amount.signum() < 0) throw new IllegalArgumentException("El monto no puede ser negativo: " + amount);
        if (commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("La comisión debe estar en [0, 1]: " + commissionRate);
        }
        BigDecimal total = money(amount);
        BigDecimal netOfVat = total.divide(BigDecimal.ONE.add(VAT_RATE), 2, RoundingMode.HALF_UP);
        BigDecimal vat = total.subtract(netOfVat);
        BigDecimal commission = money(total.multiply(commissionRate));
        return new AwardedPriceBreakdown(total, VAT_RATE, netOfVat, vat, commissionRate.stripTrailingZeros(),
                commission, total.subtract(commission));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
