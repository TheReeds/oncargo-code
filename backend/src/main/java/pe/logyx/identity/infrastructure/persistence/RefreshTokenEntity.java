package pe.logyx.identity.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.logyx.identity.domain.model.RefreshToken;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "family_id")
    public UUID familyId;

    @Column(name = "profile_id")
    public UUID profileId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64)
    public String tokenHash;

    @Column(name = "session_version")
    public int sessionVersion;

    @Column(name = "family_expires_at")
    public OffsetDateTime familyExpiresAt;

    @Column(name = "used_at")
    public OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    public OffsetDateTime revokedAt;

    @Column(name = "user_agent")
    public String userAgent;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    RefreshToken toDomain() {
        return new RefreshToken(id, familyId, profileId, tokenHash, sessionVersion, familyExpiresAt, usedAt,
                revokedAt, userAgent, createdAt);
    }

    void apply(RefreshToken t) {
        id = t.id();
        familyId = t.familyId();
        profileId = t.profileId();
        tokenHash = t.tokenHash();
        sessionVersion = t.sessionVersion();
        familyExpiresAt = t.familyExpiresAt();
        usedAt = t.usedAt();
        revokedAt = t.revokedAt();
        userAgent = t.userAgent();
        createdAt = t.createdAt();
    }
}
