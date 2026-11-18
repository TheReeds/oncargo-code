package pe.logyx.identity.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Refresh token rotativo (opt-in, conductor/móvil; F24). Una familia = un inicio de sesión; cada uso emite el
 * siguiente de la familia y marca este como usado. Reutilizar uno ya usado indica robo: se revoca la familia.
 * {@code familyExpiresAt} es absoluto (la rotación no lo extiende). {@code sessionVersion} es la del perfil al emitir:
 * si cambió (contraseña, rol, desactivación, "cerrar todas"), el token ya no vale.
 */
public final class RefreshToken {

    private final UUID id;
    private final UUID familyId;
    private final UUID profileId;
    private final String tokenHash;
    private final int sessionVersion;
    private final OffsetDateTime familyExpiresAt;
    private OffsetDateTime usedAt;
    private OffsetDateTime revokedAt;
    private final String userAgent;
    private final OffsetDateTime createdAt;

    public RefreshToken(UUID id, UUID familyId, UUID profileId, String tokenHash, int sessionVersion,
                        OffsetDateTime familyExpiresAt, OffsetDateTime usedAt, OffsetDateTime revokedAt,
                        String userAgent, OffsetDateTime createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.profileId = profileId;
        this.tokenHash = tokenHash;
        this.sessionVersion = sessionVersion;
        this.familyExpiresAt = familyExpiresAt;
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }

    /** Primer token de una familia nueva (login). */
    public static RefreshToken startFamily(UUID profileId, String tokenHash, int sessionVersion, OffsetDateTime now,
                                           java.time.Duration familyTtl, String userAgent) {
        return new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), profileId, tokenHash, sessionVersion,
                now.plus(familyTtl), null, null, truncate(userAgent), now);
    }

    /** Siguiente token de la misma familia (rotación); hereda el vencimiento absoluto. */
    public RefreshToken next(String newHash, int currentSessionVersion, OffsetDateTime now) {
        return new RefreshToken(UUID.randomUUID(), familyId, profileId, newHash, currentSessionVersion,
                familyExpiresAt, null, null, userAgent, now);
    }

    public boolean expired(OffsetDateTime now) { return !now.isBefore(familyExpiresAt); }
    public boolean used() { return usedAt != null; }
    public boolean revoked() { return revokedAt != null; }

    public void markUsed(OffsetDateTime now) { usedAt = now; }

    private static String truncate(String ua) {
        if (ua == null || ua.isBlank()) return null;
        return ua.length() > 200 ? ua.substring(0, 200) : ua;
    }

    public UUID id() { return id; }
    public UUID familyId() { return familyId; }
    public UUID profileId() { return profileId; }
    public String tokenHash() { return tokenHash; }
    public int sessionVersion() { return sessionVersion; }
    public OffsetDateTime familyExpiresAt() { return familyExpiresAt; }
    public OffsetDateTime usedAt() { return usedAt; }
    public OffsetDateTime revokedAt() { return revokedAt; }
    public String userAgent() { return userAgent; }
    public OffsetDateTime createdAt() { return createdAt; }
}
