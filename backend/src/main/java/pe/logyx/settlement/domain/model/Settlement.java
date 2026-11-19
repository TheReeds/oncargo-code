package pe.logyx.settlement.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Cobro de un envío entregado (V24 {@code shipment_settlements}, roadmap 2.12). Mutable; las transiciones las valida
 * {@code SettlementPolicy} y aquí solo se aplican. Los montos de la comisión no se guardan: salen de la tasa del envío
 * y su precio final (que no cambia desde la facturación: los ajustes quedan bloqueados).
 */
public final class Settlement {

    /** Comprobante del transportista (su factura o boleta). */
    public record Invoice(String number, LocalDate issuedOn, UUID documentId, BigDecimal amount, OffsetDateTime at,
                          UUID by) {}

    /** Pago registrado por la PYME. */
    public record Payment(PaymentMethod method, String operationNumber, LocalDate paidOn, BigDecimal amount,
                          UUID documentId, OffsetDateTime at, UUID by) {}

    /** Disputa abierta por una parte. */
    public record Dispute(OffsetDateTime at, UUID byOrgId, String reason, SettlementStatus previous) {}

    private final UUID shipmentId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private SettlementStatus status;
    private final LocalDate dueOn;
    private Invoice invoice;
    private Payment payment;
    private OffsetDateTime confirmedAt;
    private UUID confirmedBy;
    private Dispute dispute;
    private CommissionStatus commissionStatus;
    private UUID commissionCollectionId;
    private final boolean persisted;

    public Settlement(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, SettlementStatus status, LocalDate dueOn,
                      Invoice invoice, Payment payment, OffsetDateTime confirmedAt, UUID confirmedBy, Dispute dispute,
                      CommissionStatus commissionStatus, UUID commissionCollectionId, boolean persisted) {
        this.shipmentId = Objects.requireNonNull(shipmentId);
        this.requesterOrgId = Objects.requireNonNull(requesterOrgId);
        this.carrierOrgId = Objects.requireNonNull(carrierOrgId);
        this.status = Objects.requireNonNull(status);
        this.dueOn = Objects.requireNonNull(dueOn);
        this.invoice = invoice;
        this.payment = payment;
        this.confirmedAt = confirmedAt;
        this.confirmedBy = confirmedBy;
        this.dispute = dispute;
        this.commissionStatus = Objects.requireNonNull(commissionStatus);
        this.commissionCollectionId = commissionCollectionId;
        this.persisted = persisted;
    }

    /** Cobro recién abierto (aún sin guardar) de un envío entregado. */
    public static Settlement open(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, LocalDate dueOn,
                                  boolean commissionApplies) {
        return new Settlement(shipmentId, requesterOrgId, carrierOrgId, SettlementStatus.PENDING_INVOICE, dueOn, null,
                null, null, null, null, commissionApplies ? CommissionStatus.PENDING : CommissionStatus.NOT_APPLICABLE,
                null, false);
    }

    public void invoice(Invoice i) {
        this.invoice = i;
        this.status = SettlementStatus.INVOICED;
    }

    public void pay(Payment p) {
        this.payment = p;
        this.status = SettlementStatus.PAID;
    }

    public void confirm(OffsetDateTime at, UUID by) {
        this.confirmedAt = at;
        this.confirmedBy = by;
        this.status = SettlementStatus.CONFIRMED;
    }

    public void openDispute(Dispute d) {
        this.dispute = d;
        this.status = SettlementStatus.DISPUTED;
    }

    public void closeDispute(SettlementStatus to) {
        this.status = to;
        this.dispute = null;
        if (to == SettlementStatus.PENDING_INVOICE) {
            this.invoice = null;
            this.payment = null;
            this.confirmedAt = null;
            this.confirmedBy = null;
        } else if (to == SettlementStatus.INVOICED) {
            this.payment = null;
            this.confirmedAt = null;
            this.confirmedBy = null;
        } else if (to == SettlementStatus.PAID) {
            this.confirmedAt = null;
            this.confirmedBy = null;
        }
    }

    public void collectCommission(UUID collectionId) {
        this.commissionStatus = CommissionStatus.COLLECTED;
        this.commissionCollectionId = collectionId;
    }

    public UUID shipmentId() { return shipmentId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public SettlementStatus status() { return status; }
    public LocalDate dueOn() { return dueOn; }
    public Invoice invoice() { return invoice; }
    public Payment payment() { return payment; }
    public OffsetDateTime confirmedAt() { return confirmedAt; }
    public UUID confirmedBy() { return confirmedBy; }
    public Dispute dispute() { return dispute; }
    public CommissionStatus commissionStatus() { return commissionStatus; }
    public UUID commissionCollectionId() { return commissionCollectionId; }
    /** Ya tiene fila en la BD (si no, es la vista por omisión de un envío entregado). */
    public boolean persisted() { return persisted; }
}
