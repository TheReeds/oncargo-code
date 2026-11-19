package pe.logyx.pricing.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.pricing.domain.CommissionPolicy;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;
import pe.logyx.shared.config.SystemParameters;

import java.math.BigDecimal;

/**
 * API de comisión para otros módulos (decisión del dueño, 2026-11-17). La tasa es el parámetro de sistema
 * {@code commission_rate} (8 % inicial, editable por el operador con historial).
 *
 * <ul>
 *   <li>{@link #currentRate()}: la tasa vigente ahora. {@code AcceptBidUseCase} la guarda en
 *       {@code shipments.commission_rate} (V18) al adjudicar, para que un cambio posterior no altere ese envío.</li>
 *   <li>{@link #breakdown(BigDecimal)}: desglose con la tasa vigente (p. ej. "cobras neto" en el {@code BidSheet}
 *       antes de adjudicar).</li>
 *   <li>{@link #breakdown(BigDecimal, BigDecimal)}: desglose con la tasa guardada de un envío ya creado (vistas,
 *       reportes, exportaciones).</li>
 * </ul>
 * Sin transacción propia: la lectura del parámetro pasa por una caché de 10 s.
 */
@ApplicationScoped
public class CommissionService {

    @Inject SystemParameters parameters;

    public BigDecimal currentRate() {
        return parameters.commissionRate();
    }

    public AwardedPriceBreakdown breakdown(BigDecimal amount) {
        return CommissionPolicy.breakdown(amount, currentRate());
    }

    /** {@code commissionRate} nulo (envío anterior a V18) = sin comisión registrada: se desglosa solo el IGV (tasa 0). */
    public AwardedPriceBreakdown breakdown(BigDecimal amount, BigDecimal commissionRate) {
        return CommissionPolicy.breakdown(amount, commissionRate == null ? BigDecimal.ZERO : commissionRate);
    }
}
