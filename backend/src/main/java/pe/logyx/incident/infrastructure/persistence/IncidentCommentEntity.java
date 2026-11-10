package pe.logyx.incident.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.incident.domain.model.IncidentComment;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "incident_comments")
public class IncidentCommentEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "incident_id")
    public UUID incidentId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "author_org_id")
    public UUID authorOrgId;

    @Column(name = "author_profile_id")
    public UUID authorProfileId;

    public String body;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    IncidentComment toDomain() {
        return new IncidentComment(id, incidentId, requesterOrgId, carrierOrgId, authorOrgId, authorProfileId, body,
                createdAt);
    }

    static IncidentCommentEntity from(IncidentComment c) {
        IncidentCommentEntity e = new IncidentCommentEntity();
        e.id = c.id();
        e.incidentId = c.incidentId();
        e.requesterOrgId = c.requesterOrgId();
        e.carrierOrgId = c.carrierOrgId();
        e.authorOrgId = c.authorOrgId();
        e.authorProfileId = c.authorProfileId();
        e.body = c.body();
        e.createdAt = c.createdAt();
        return e;
    }
}
