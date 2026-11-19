package pe.logyx.incident.infrastructure.persistence;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.incident.domain.model.Incident;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class IncidentEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "shipment_id")
    public UUID shipmentId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "reported_by_org_id")
    public UUID reportedByOrgId;

    @Column(name = "reported_by_profile_id")
    public UUID reportedByProfileId;

    @Enumerated(EnumType.STRING)
    public IncidentCategory category;

    public String description;

    @Enumerated(EnumType.STRING)
    public IncidentStatus status;

    @Column(name = "resolution_notes")
    public String resolutionNotes;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @Column(name = "resolved_at")
    public OffsetDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_outcome")
    public pe.logyx.incident.domain.model.IncidentShipmentOutcome shipmentOutcome;

    // ----- V23 (roadmap 2.7)
    @Enumerated(EnumType.STRING)
    public pe.logyx.incident.domain.model.IncidentSeverity severity;
    public boolean blocking;
    @Column(name = "claimed_amount") public java.math.BigDecimal claimedAmount;
    @Column(name = "assignee_profile_id") public UUID assigneeProfileId;
    @Column(name = "due_at") public OffsetDateTime dueAt;
    @Column(name = "withdrawn_at") public OffsetDateTime withdrawnAt;
    @Column(name = "reopened_at") public OffsetDateTime reopenedAt;
    @Column(name = "reopen_count") public int reopenCount;
    public java.math.BigDecimal latitude;
    public java.math.BigDecimal longitude;
    @Column(name = "occurred_at") public OffsetDateTime occurredAt;
    @Column(name = "adjustment_id") public UUID adjustmentId;

    Incident toDomain() {
        return new Incident(id, shipmentId, requesterOrgId, carrierOrgId, reportedByOrgId, reportedByProfileId,
                category, description, status, resolutionNotes, createdAt, updatedAt, resolvedAt, shipmentOutcome)
                .restoreV2(severity, blocking, claimedAmount, assigneeProfileId, dueAt, withdrawnAt, reopenedAt,
                        reopenCount, latitude == null || longitude == null ? null
                                : new Incident.Location(latitude, longitude), occurredAt, adjustmentId);
    }

    void apply(Incident i) {
        id = i.id();
        shipmentId = i.shipmentId();
        requesterOrgId = i.requesterOrgId();
        carrierOrgId = i.carrierOrgId();
        reportedByOrgId = i.reportedByOrgId();
        reportedByProfileId = i.reportedByProfileId();
        category = i.category();
        description = i.description();
        status = i.status();
        resolutionNotes = i.resolutionNotes();
        createdAt = i.createdAt();
        updatedAt = i.updatedAt();
        resolvedAt = i.resolvedAt();
        shipmentOutcome = i.shipmentOutcome();
        severity = i.severity();
        blocking = i.blocking();
        claimedAmount = i.claimedAmount();
        assigneeProfileId = i.assigneeProfileId();
        dueAt = i.dueAt();
        withdrawnAt = i.withdrawnAt();
        reopenedAt = i.reopenedAt();
        reopenCount = i.reopenCount();
        latitude = i.location() == null ? null : i.location().latitude();
        longitude = i.location() == null ? null : i.location().longitude();
        occurredAt = i.occurredAt();
        adjustmentId = i.adjustmentId();
    }
}
