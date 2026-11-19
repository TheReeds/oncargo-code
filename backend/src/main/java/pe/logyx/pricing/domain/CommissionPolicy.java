package pe.logyx.pricing.domain;

import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Comisión de OnCargo y desglose del IGV (decisión del dueño del producto, 2026-11-17, base ajustada el 2026-11-18).
 * Dominio puro y sin estado: lo reutilizan el {@code BidSheet} ("cobras S/ X neto"), la adjudicación (tasa vigente
 * guardada en el envío), la vista de la PYME (IGV desglosado) y los reportes.
 *
 * <ul>
 *   <li><b>Quién paga:</b> la comisión es un porcentaje que OnCargo retiene al <b>transportista</b>; la PYME paga el
 *       precio adjudicado, sin recargo.</li>
 *   <li><b>IGV 18 %:</b> el precio adjudicado es final e incluye IGV; se desglosa
 *       {@code valor de venta = precio / 1.18} (2 decimales HALF_UP) e {@code IGV = precio − valor de venta}.</li>
 *   <li><b>Base:</b> la comisión se calcula sobre el <b>valor de venta sin IGV</b> (el IGV es un tributo que el
 *       transportista recauda para SUNAT, no ingreso suyo): {@code comisión = valor de venta × tasa}. OnCargo factura
 *       su comisión con su propio IGV: {@code IGV de la comisión = comisión × 0.18}.</li>
 *   <li><b>Neto del transportista</b> = {@code precio − comisión − IGV de la comisión}. Ejemplo: S/ 1,180 al 8 %:
 *       valor de venta 1,000, IGV 180, comisión 80, IGV de la comisión 14.40, neto 1,085.60.</li>
 * </ul>
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
        BigDecimal commission = money(netOfVat.multiply(commissionRate));
        BigDecimal commissionVat = money(commission.multiply(VAT_RATE));
        return new AwardedPriceBreakdown(total, VAT_RATE, netOfVat, vat, commissionRate.stripTrailingZeros(),
                commission, commissionVat, total.subtract(commission).subtract(commissionVat));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
