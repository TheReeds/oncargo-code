package pe.logyx.shipment.infrastructure.web;

import io.quarkus.arc.Arc;
import pe.logyx.pricing.application.CommissionService;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;

/**
 * Precio adjudicado desglosado (decisión del dueño, ADR-0010): {@code amount} incluye IGV; {@code netOfVat} + {@code vat}
 * = {@code amount}. La PYME ve solo ese desglose. El transportista (y el operador) ven además la comisión de OnCargo
 * calculada con la tasa que quedó guardada en el envío al adjudicar ({@code commissionRate}), su IGV y
 * {@code carrierNet} ("cobras S/ X neto"); para la PYME y el conductor esos cuatro campos son nulos.
 * Envíos anteriores a V18 (tasa nula): sin comisión, solo IGV.
 */
public record PriceBreakdownView(BigDecimal amount, BigDecimal vatRate, BigDecimal netOfVat, BigDecimal vat,
                                 BigDecimal commissionRate, BigDecimal commission, BigDecimal commissionVat,
                                 BigDecimal carrierNet) {

    /** Se arma con el rol de quien mira (JWT): la comisión no se muestra a la PYME ni al conductor. */
    static PriceBreakdownView of(BigDecimal agreedPrice, BigDecimal storedRate) {
        if (agreedPrice == null) return null;
        var container = Arc.container();
        AwardedPriceBreakdown b = container.instance(CommissionService.class).get().breakdown(agreedPrice, storedRate);
        boolean seesCommission = false;
        var tenant = container.instance(TenantContext.class).get();
        if (tenant.isAuthenticated()) {
            OrgType type = tenant.orgType();
            seesCommission = type == OrgType.OPERATOR || (type == OrgType.CARRIER && !"DRIVER".equals(tenant.role()));
        }
        if (!seesCommission || storedRate == null) {
            return new PriceBreakdownView(b.amount(), b.vatRate(), b.netOfVat(), b.vat(), null, null, null, null);
        }
        return new PriceBreakdownView(b.amount(), b.vatRate(), b.netOfVat(), b.vat(), b.commissionRate(),
                b.commission(), b.commissionVat(), b.carrierNet());
    }
}
