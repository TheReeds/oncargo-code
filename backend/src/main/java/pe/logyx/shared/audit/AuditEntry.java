package pe.logyx.shared.audit;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditEntry extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "occurred_at", nullable = false)
    public OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(name = "actor_profile_id")
    public UUID actorProfileId;

    @Column(name = "organization_id")
    public UUID organizationId;

    public String action;
    public String entity;

    @Column(name = "entity_id")
    public UUID entityId;

    public String detail;
}
