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

    @Column(name = "session_version")
    public int sessionVersion;

    @Column(name = "last_login_at")
    public OffsetDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    public OffsetDateTime passwordChangedAt;

    @Column(name = "invitation_pending")
    public boolean invitationPending;

    @Column(name = "invited_by")
    public UUID invitedBy;

    Profile toDomain() {
        return new Profile(id, organizationId, email, passwordHash, fullName, role, active,
                consentAcceptedAt, createdAt, sessionVersion, lastLoginAt, passwordChangedAt, invitationPending,
                invitedBy);
    }

    static ProfileEntity from(Profile p) {
        ProfileEntity e = new ProfileEntity();
        e.apply(p);
        return e;
    }

    void apply(Profile p) {
        ProfileEntity e = this;
        e.id = p.id();
        e.organizationId = p.organizationId();
        e.email = p.email();
        e.passwordHash = p.passwordHash();
        e.fullName = p.fullName();
        e.role = p.role();
        e.active = p.active();
        e.consentAcceptedAt = p.consentAcceptedAt();
        e.createdAt = p.createdAt();
        e.sessionVersion = p.sessionVersion();
        e.lastLoginAt = p.lastLoginAt();
        e.passwordChangedAt = p.passwordChangedAt();
        e.invitationPending = p.invitationPending();
        e.invitedBy = p.invitedBy();
    }
}
