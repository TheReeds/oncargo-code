package pe.logyx.identity.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Usuario de la plataforma; siempre pertenece a una organización.
 *
 * <p>Roles de organización (F03): {@code ADMIN} todo, {@code OPS} opera (sin usuarios, perfil de la organización ni
 * cuentas de conductores), {@code VIEWER} solo lectura. {@code DRIVER} es el conductor (grupo JWT propio).
 *
 * <p>{@code sessionVersion} es la versión de sesión: viaja en el JWT (claim {@code sv}) y, al subir, invalida todos
 * los tokens emitidos antes (cambio de contraseña o de rol, desactivación, "cerrar todas las sesiones").
 */
public final class Profile {

    public enum Role { ADMIN, OPS, VIEWER, DRIVER }

    private final UUID id;
    private final UUID organizationId;
    private final String email;
    private String passwordHash;
    private final String fullName;
    private Role role;
    private boolean active;
    private OffsetDateTime consentAcceptedAt;
    private final OffsetDateTime createdAt;
    private int sessionVersion;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime passwordChangedAt;
    private boolean invitationPending;
    private final UUID invitedBy;
    /** V23 (2.15): suspensión por el operador; mientras exista, su ADMIN no lo reactiva (LOGYX-USR-007). */
    private OffsetDateTime operatorSuspendedAt;
    private String operatorSuspensionReason;

    public Profile(UUID id, UUID organizationId, String email, String passwordHash, String fullName, Role role,
                   boolean active, OffsetDateTime consentAcceptedAt, OffsetDateTime createdAt) {
        this(id, organizationId, email, passwordHash, fullName, role, active, consentAcceptedAt, createdAt,
                0, null, null, false, null);
    }

    public Profile(UUID id, UUID organizationId, String email, String passwordHash, String fullName, Role role,
                   boolean active, OffsetDateTime consentAcceptedAt, OffsetDateTime createdAt, int sessionVersion,
                   OffsetDateTime lastLoginAt, OffsetDateTime passwordChangedAt, boolean invitationPending,
                   UUID invitedBy) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
        this.consentAcceptedAt = consentAcceptedAt;
        this.createdAt = createdAt;
        this.sessionVersion = sessionVersion;
        this.lastLoginAt = lastLoginAt;
        this.passwordChangedAt = passwordChangedAt;
        this.invitationPending = invitationPending;
        this.invitedBy = invitedBy;
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

    /**
     * Usuario invitado por un ADMIN (F03): sin contraseña utilizable ({@code unusableHash} es el hash de un secreto
     * aleatorio que nadie conoce) hasta que canjee la invitación. El consentimiento se registra al aceptarla.
     */
    public static Profile invite(UUID organizationId, String email, String unusableHash, String fullName, Role role,
                                 UUID invitedBy, OffsetDateTime now) {
        if (role == Role.DRIVER) throw new IllegalArgumentException("Los conductores se crean desde la flota");
        return new Profile(UUID.randomUUID(), organizationId, normalizeEmail(email), unusableHash, fullName.trim(),
                role, true, now, now, 0, null, null, true, invitedBy);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** Puede iniciar sesión: activo y con contraseña propia (no una invitación pendiente). */
    public boolean canLogIn() { return active && !invitationPending; }

    /** Nueva contraseña (cambio, restablecimiento o invitación aceptada): invalida las sesiones anteriores. */
    public void changePassword(String newHash, OffsetDateTime now) {
        passwordHash = newHash;
        passwordChangedAt = now;
        invitationPending = false;
        revokeSessions();
    }

    /** Aceptó la invitación: registra el consentimiento (Ley N° 29733) en ese momento. */
    public void acceptInvitation(String newHash, OffsetDateTime now) {
        consentAcceptedAt = now;
        changePassword(newHash, now);
    }

    public void changeRole(Role newRole) {
        if (newRole == role) return;
        role = newRole;
        revokeSessions();
    }

    public void deactivate() {
        if (!active) return;
        active = false;
        revokeSessions();
    }

    public void reactivate() {
        if (operatorSuspendedAt != null) {
            throw pe.logyx.shared.domain.BusinessException.conflict("LOGYX-USR-007",
                    "OnCargo suspendió este usuario: solo el operador lo reactiva");
        }
        active = true;
    }

    /** El operador suspende al usuario (2.15): no inicia sesión y se cierran sus sesiones. */
    public void suspendByOperator(String reason, OffsetDateTime now) {
        operatorSuspendedAt = now;
        operatorSuspensionReason = reason;
        if (active) {
            active = false;
            revokeSessions();
        }
    }

    /** El operador levanta su suspensión y reactiva al usuario. */
    public void reactivateByOperator() {
        operatorSuspendedAt = null;
        operatorSuspensionReason = null;
        active = true;
    }

    /** Restaura lo guardado (persistencia). */
    public Profile restoreOperatorSuspension(OffsetDateTime at, String reason) {
        operatorSuspendedAt = at;
        operatorSuspensionReason = reason;
        return this;
    }

    public OffsetDateTime operatorSuspendedAt() { return operatorSuspendedAt; }
    public String operatorSuspensionReason() { return operatorSuspensionReason; }

    /** "Cerrar sesión en todos los dispositivos" (y efecto de los cambios de arriba). */
    public void revokeSessions() {
        sessionVersion++;
    }

    public void recordLogin(OffsetDateTime now) {
        lastLoginAt = now;
    }

    public boolean isDriver() { return role == Role.DRIVER; }
    public boolean isOrgAdmin() { return role == Role.ADMIN; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String fullName() { return fullName; }
    public Role role() { return role; }
    public boolean active() { return active; }
    public OffsetDateTime consentAcceptedAt() { return consentAcceptedAt; }
    public OffsetDateTime createdAt() { return createdAt; }
    public int sessionVersion() { return sessionVersion; }
    public OffsetDateTime lastLoginAt() { return lastLoginAt; }
    public OffsetDateTime passwordChangedAt() { return passwordChangedAt; }
    public boolean invitationPending() { return invitationPending; }
    public UUID invitedBy() { return invitedBy; }
}
