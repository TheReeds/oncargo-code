package pe.logyx.realtime.domain.model;

import pe.logyx.shared.tenancy.OrgType;

import java.util.Objects;
import java.util.UUID;

/**
 * Destinatario explícito de un evento en tiempo real (ADR-0007). Exactamente una de tres formas:
 * <ul>
 *   <li>{@link #org(UUID)}: los usuarios de esa organización, salvo sus conductores;</li>
 *   <li>{@link #user(UUID, UUID)}: un usuario concreto de la organización (el conductor del envío);</li>
 *   <li>{@link #broadcast(OrgType)}: todas las organizaciones de un tipo (solo datos públicos para ellas).</li>
 * </ul>
 */
public record Recipient(UUID orgId, OrgType orgType, UUID userId) {

    public Recipient {
        if (orgId == null && (orgType == null || userId != null)) {
            throw new IllegalArgumentException("Destinatario inválido: falta organización");
        }
    }

    public static Recipient org(UUID orgId) {
        return new Recipient(Objects.requireNonNull(orgId), null, null);
    }

    public static Recipient user(UUID orgId, UUID userId) {
        return new Recipient(Objects.requireNonNull(orgId), null, Objects.requireNonNull(userId));
    }

    public static Recipient broadcast(OrgType type) {
        return new Recipient(null, Objects.requireNonNull(type), null);
    }
}
