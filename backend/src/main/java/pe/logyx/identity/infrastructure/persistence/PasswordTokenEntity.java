package pe.logyx.identity.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.logyx.identity.domain.model.PasswordToken;
import pe.logyx.identity.domain.model.PasswordTokenPurpose;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_tokens")
public class PasswordTokenEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "profile_id")
    public UUID profileId;

    @Enumerated(EnumType.STRING)
    public PasswordTokenPurpose purpose;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64)
    public String tokenHash;

    @Column(name = "expires_at")
    public OffsetDateTime expiresAt;

    @Column(name = "used_at")
    public OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    public OffsetDateTime revokedAt;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    PasswordToken toDomain() {
        return new PasswordToken(id, profileId, purpose, tokenHash, expiresAt, usedAt, revokedAt, createdAt);
    }

    void apply(PasswordToken t) {
        id = t.id();
        profileId = t.profileId();
        purpose = t.purpose();
        tokenHash = t.tokenHash();
        expiresAt = t.expiresAt();
        usedAt = t.usedAt();
        revokedAt = t.revokedAt();
        createdAt = t.createdAt();
    }
}
