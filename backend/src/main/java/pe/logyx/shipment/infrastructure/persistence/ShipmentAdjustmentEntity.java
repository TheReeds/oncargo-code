package pe.logyx.shipment.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import pe.logyx.shipment.domain.model.AdjustmentStatus;
import pe.logyx.shipment.domain.model.AdjustmentType;
import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code @DynamicUpdate}: V23 solo concede UPDATE sobre las columnas de estado y decisión. */
@Entity
@Table(name = "shipment_adjustments")
@DynamicUpdate
public class ShipmentAdjustmentEntity extends PanacheEntityBase {

    @Id public UUID id;
    @Column(name = "shipment_id") public UUID shipmentId;
    @Column(name = "stop_id") public UUID stopId;
    @Column(name = "requester_org_id") public UUID requesterOrgId;
    @Column(name = "carrier_org_id") public UUID carrierOrgId;
    @Enumerated(EnumType.STRING) public AdjustmentType type;
    public BigDecimal amount;
    public String note;
    @Column(name = "evidence_document_id") public UUID evidenceDocumentId;
    @Enumerated(EnumType.STRING) public AdjustmentStatus status;
    @Column(name = "proposed_by_org_id") public UUID proposedByOrgId;
    @Column(name = "proposed_by_profile_id") public UUID proposedByProfileId;
    @Column(name = "created_at") public OffsetDateTime createdAt;
    @Column(name = "respond_by") public OffsetDateTime respondBy;
    @Column(name = "escalated_at") public OffsetDateTime escalatedAt;
    @Column(name = "decided_at") public OffsetDateTime decidedAt;
    @Column(name = "decided_by_profile_id") public UUID decidedByProfileId;
    @Column(name = "decided_by_org_id") public UUID decidedByOrgId;
    @Column(name = "decision_note") public String decisionNote;

    ShipmentAdjustment toDomain() {
        var d = decidedAt == null ? null
                : new ShipmentAdjustment.Decision(decidedByOrgId, decidedByProfileId, decidedAt, decisionNote);
        return new ShipmentAdjustment(id, shipmentId, stopId, requesterOrgId, carrierOrgId, type, amount, note,
                evidenceDocumentId, status, proposedByProfileId, createdAt, respondBy, escalatedAt, d);
    }

    static ShipmentAdjustmentEntity of(ShipmentAdjustment a) {
        var e = new ShipmentAdjustmentEntity();
        e.id = a.id();
        e.shipmentId = a.shipmentId();
        e.stopId = a.stopId();
        e.requesterOrgId = a.requesterOrgId();
        e.carrierOrgId = a.carrierOrgId();
        e.type = a.type();
        e.amount = a.amount();
        e.note = a.note();
        e.evidenceDocumentId = a.evidenceDocumentId();
        e.proposedByOrgId = a.carrierOrgId();
        e.proposedByProfileId = a.proposedByProfileId();
        e.createdAt = a.createdAt();
        e.respondBy = a.respondBy();
        e.applyState(a);
        return e;
    }

    void applyState(ShipmentAdjustment a) {
        status = a.status();
        escalatedAt = a.escalatedAt();
        var d = a.decision();
        decidedAt = d == null ? null : d.at();
        decidedByOrgId = d == null ? null : d.byOrgId();
        decidedByProfileId = d == null ? null : d.byProfileId();
        decisionNote = d == null ? null : d.note();
    }
}
