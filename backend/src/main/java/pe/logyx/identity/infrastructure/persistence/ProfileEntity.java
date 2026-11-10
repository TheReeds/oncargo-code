package pe.logyx.identity.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.identity.domain.model.Profile;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class ProfileEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    public String email;

    @Column(name = "password_hash")
    public String passwordHash;

    @Column(name = "full_name")
    public String fullName;

    @Enumerated(EnumType.STRING)
    public Profile.Role role;

    public boolean active;

    @Column(name = "consent_accepted_at")
    public OffsetDateTime consentAcceptedAt;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Profile toDomain() {
        return new Profile(id, organizationId, email, passwordHash, fullName, role, active,
                consentAcceptedAt, createdAt);
    }

    static ProfileEntity from(Profile p) {
        ProfileEntity e = new ProfileEntity();
        e.id = p.id();
        e.organizationId = p.organizationId();
        e.email = p.email();
        e.passwordHash = p.passwordHash();
        e.fullName = p.fullName();
        e.role = p.role();
        e.active = p.active();
        e.consentAcceptedAt = p.consentAcceptedAt();
        e.createdAt = p.createdAt();
        return e;
    }
}
