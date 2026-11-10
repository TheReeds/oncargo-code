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

    Incident toDomain() {
        return new Incident(id, shipmentId, requesterOrgId, carrierOrgId, reportedByOrgId, reportedByProfileId,
                category, description, status, resolutionNotes, createdAt, updatedAt, resolvedAt);
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
    }
}
