package pe.logyx.identity.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Token de un solo uso para definir una contraseña: {@code RESET} (olvidé mi contraseña) o {@code INVITE}
 * (invitación de un ADMIN). Solo se guarda el hash; vence a los {@code logyx.password-reset.ttl} /
 * {@code logyx.invitation.ttl}. Pedir otro invalida los anteriores sin usar ({@code revokedAt}).
 */
public final class PasswordToken {


    private final UUID id;
    private final UUID profileId;
    private final PasswordTokenPurpose purpose;
    private final String tokenHash;
    private final OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;
    private final OffsetDateTime revokedAt;
    private final OffsetDateTime createdAt;

    public PasswordToken(UUID id, UUID profileId, PasswordTokenPurpose purpose, String tokenHash, OffsetDateTime expiresAt,
                         OffsetDateTime usedAt, OffsetDateTime revokedAt, OffsetDateTime createdAt) {
        this.id = id;
        this.profileId = profileId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
        this.createdAt = createdAt;
    }

    public static PasswordToken issue(UUID profileId, PasswordTokenPurpose purpose, String tokenHash, OffsetDateTime now,
                                      java.time.Duration ttl) {
        return new PasswordToken(UUID.randomUUID(), profileId, purpose, tokenHash, now.plus(ttl), null, null, now);
    }

    public boolean usable(OffsetDateTime now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(OffsetDateTime now) {
        usedAt = now;
    }

    public UUID id() { return id; }
    public UUID profileId() { return profileId; }
    public PasswordTokenPurpose purpose() { return purpose; }
    public String tokenHash() { return tokenHash; }
    public OffsetDateTime expiresAt() { return expiresAt; }
    public OffsetDateTime usedAt() { return usedAt; }
    public OffsetDateTime revokedAt() { return revokedAt; }
    public OffsetDateTime createdAt() { return createdAt; }
}
