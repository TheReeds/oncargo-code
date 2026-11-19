package pe.logyx.shipment.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.logyx.shipment.application.AdjustmentsOverview;
import pe.logyx.shipment.domain.model.AdjustmentStatus;
import pe.logyx.shipment.domain.model.AdjustmentType;
import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Ajustes del envío (roadmap 2.6, ADR-0012). Montos con IGV. */
public final class AdjustmentDtos {
    private AdjustmentDtos() {}

    /** {@code amount}: con IGV, distinto de 0; negativo solo en WEIGHT_DIFF u OTHER (descuento). */
    public record ProposeAdjustment(@NotNull AdjustmentType type, @NotNull BigDecimal amount,
                                    @Size(max = 500) String note, UUID stopId, UUID evidenceDocumentId) {}

    /** Nota de la decisión: obligatoria al rechazar (LOGYX-ADJ-004). */
    public record DecideAdjustment(@Size(max = 500) String note) {}

    /**
     * {@code escalated}: pasó {@code respondBy} sin respuesta de la PYME y lo decide el operador. {@code decidedByOperator}:
     * la decisión la tomó el operador.
     */
    public record AdjustmentView(UUID id, UUID shipmentId, UUID stopId, AdjustmentType type, BigDecimal amount,
                                 String note, UUID evidenceDocumentId, AdjustmentStatus status,
                                 OffsetDateTime createdAt, OffsetDateTime respondBy, boolean escalated,
                                 OffsetDateTime escalatedAt, OffsetDateTime decidedAt, String decisionNote,
                                 UUID decidedByOrgId, UUID requesterOrgId, UUID carrierOrgId) {
        static AdjustmentView of(ShipmentAdjustment a) {
            var d = a.decision();
            return new AdjustmentView(a.id(), a.shipmentId(), a.stopId(), a.type(), a.amount(), a.note(),
                    a.evidenceDocumentId(), a.status(), a.createdAt(), a.respondBy(), a.isEscalated(),
                    a.escalatedAt(), d == null ? null : d.at(), d == null ? null : d.note(),
                    d == null ? null : d.byOrgId(), a.requesterOrgId(), a.carrierOrgId());
        }
    }

    /** Sugerencia que el transportista puede proponer con un toque (no se cobra sola). */
    public record AdjustmentSuggestionView(AdjustmentType type, UUID stopId, BigDecimal amount, String detail) {}

    /**
     * Ajustes del envío con el precio final (acordado + aceptados) y su desglose con IGV (y comisión para el
     * transportista y el operador, con la tasa guardada al adjudicar). {@code suggestions} solo para el transportista.
     */
    public record ShipmentAdjustmentsView(BigDecimal agreedPrice, BigDecimal acceptedTotal, BigDecimal finalPrice,
                                          PriceBreakdownView finalPriceBreakdown, List<AdjustmentView> adjustments,
                                          List<AdjustmentSuggestionView> suggestions) {
        static ShipmentAdjustmentsView of(AdjustmentsOverview o) {
            return new ShipmentAdjustmentsView(o.agreedPrice(), o.acceptedTotal(), o.finalPrice(),
                    PriceBreakdownView.of(o.finalPrice(), o.commissionRate()),
                    o.adjustments().stream().map(AdjustmentView::of).toList(),
                    o.suggestions().stream().map(x -> new AdjustmentSuggestionView(x.type(), x.stopId(), x.amount(),
                            x.detail())).toList());
        }
    }
}
