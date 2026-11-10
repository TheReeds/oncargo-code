package pe.logyx.identity.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Usuario de la plataforma; siempre pertenece a una organización. */
public final class Profile {

    public enum Role { ADMIN, OPS, VIEWER, DRIVER }

    private final UUID id;
    private final UUID organizationId;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean active;
    private final OffsetDateTime consentAcceptedAt;
    private final OffsetDateTime createdAt;

    public Profile(UUID id, UUID organizationId, String email, String passwordHash, String fullName, Role role,
                   boolean active, OffsetDateTime consentAcceptedAt, OffsetDateTime createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
        this.consentAcceptedAt = consentAcceptedAt;
        this.createdAt = createdAt;
    }

    /**
     * Nuevo usuario activo. {@code consentAcceptedAt} registra el consentimiento
     * de la Ley N° 29733 (RNF18).
     */
    public static Profile create(UUID organizationId, String email, String passwordHash, String fullName,
                                 Role role, OffsetDateTime consentAcceptedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Profile(UUID.randomUUID(), organizationId, normalizeEmail(email), passwordHash,
                fullName.trim(), role, true, consentAcceptedAt, now);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    public boolean isDriver() { return role == Role.DRIVER; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String fullName() { return fullName; }
    public Role role() { return role; }
    public boolean active() { return active; }
    public OffsetDateTime consentAcceptedAt() { return consentAcceptedAt; }
    public OffsetDateTime createdAt() { return createdAt; }
}
