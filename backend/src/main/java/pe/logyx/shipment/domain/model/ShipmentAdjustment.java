package pe.logyx.shipment.domain.model;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.AdjustmentPolicy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ajuste del precio de un envío (roadmap 2.6, ADR-0012): lo propone el transportista (oficina o conductor asignado),
 * lo acepta o rechaza la PYME dentro de {@code respondBy}; si no responde, se escala al operador, que decide. El precio
 * final del envío = acordado + ajustes aceptados. Montos con IGV, como el precio adjudicado.
 */
public final class ShipmentAdjustment {

    /** Quién decidió (PYME u operador), cuándo y con qué nota. */
    public record Decision(UUID byOrgId, UUID byProfileId, OffsetDateTime at, String note) {}

    private final UUID id;
    private final UUID shipmentId;
    private final UUID stopId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final AdjustmentType type;
    private final BigDecimal amount;
    private final String note;
    private final UUID evidenceDocumentId;
    private AdjustmentStatus status;
    private final UUID proposedByProfileId;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime respondBy;
    private OffsetDateTime escalatedAt;
    private Decision decision;

    public ShipmentAdjustment(UUID id, UUID shipmentId, UUID stopId, UUID requesterOrgId, UUID carrierOrgId,
                              AdjustmentType type, BigDecimal amount, String note, UUID evidenceDocumentId,
                              AdjustmentStatus status, UUID proposedByProfileId, OffsetDateTime createdAt,
                              OffsetDateTime respondBy, OffsetDateTime escalatedAt, Decision decision) {
        this.id = id;
        this.shipmentId = shipmentId;
        this.stopId = stopId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.type = type;
        this.amount = amount;
        this.note = note;
        this.evidenceDocumentId = evidenceDocumentId;
        this.status = status;
        this.proposedByProfileId = proposedByProfileId;
        this.createdAt = createdAt;
        this.respondBy = respondBy;
        this.escalatedAt = escalatedAt;
        this.decision = decision;
    }

    /** Nueva propuesta, ya validada por {@link AdjustmentPolicy#validateProposal}. */
    public static ShipmentAdjustment propose(Shipment s, UUID stopId, AdjustmentType type, BigDecimal amount,
                                             String note, UUID evidenceDocumentId, UUID proposerProfileId,
                                             OffsetDateTime now, Duration responseWindow) {
        return new ShipmentAdjustment(UUID.randomUUID(), s.id(), stopId, s.requesterOrgId(), s.carrierOrgId(), type,
                amount, note, evidenceDocumentId, AdjustmentStatus.PENDING, proposerProfileId, now,
                now.plus(responseWindow), null, null);
    }

    /** La PYME acepta o rechaza (antes de escalar), o el operador decide un ajuste escalado. */
    public void decide(boolean accept, UUID byOrgId, UUID byProfileId, String note, OffsetDateTime now) {
        ensurePending();
        String clean = AdjustmentPolicy.decisionNote(accept, note);
        status = accept ? AdjustmentStatus.ACCEPTED : AdjustmentStatus.REJECTED;
        decision = new Decision(byOrgId, byProfileId, now, clean);
    }

    /** Quien propuso lo retira mientras está pendiente. */
    public void withdraw(UUID byOrgId, UUID byProfileId, OffsetDateTime now) {
        ensurePending();
        status = AdjustmentStatus.WITHDRAWN;
        decision = new Decision(byOrgId, byProfileId, now, null);
    }

    /** Venció el plazo de la PYME: pasa al operador. Idempotente. */
    public boolean escalate(OffsetDateTime now) {
        if (status != AdjustmentStatus.PENDING || escalatedAt != null) return false;
        escalatedAt = now;
        return true;
    }

    private void ensurePending() {
        if (status != AdjustmentStatus.PENDING) {
            throw BusinessException.conflict("LOGYX-ADJ-007", "Este ajuste ya se resolvió");
        }
    }

    public boolean isPending() { return status == AdjustmentStatus.PENDING; }
    public boolean isEscalated() { return escalatedAt != null; }
    /** Suma al precio final. */
    public boolean counts() { return status == AdjustmentStatus.ACCEPTED; }

    public UUID id() { return id; }
    public UUID shipmentId() { return shipmentId; }
    public UUID stopId() { return stopId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public AdjustmentType type() { return type; }
    public BigDecimal amount() { return amount; }
    public String note() { return note; }
    public UUID evidenceDocumentId() { return evidenceDocumentId; }
    public AdjustmentStatus status() { return status; }
    public UUID proposedByProfileId() { return proposedByProfileId; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime respondBy() { return respondBy; }
    public OffsetDateTime escalatedAt() { return escalatedAt; }
    public Decision decision() { return decision; }
}
