package pe.logyx.identity.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aceptaciones de los Términos y condiciones (V18, {@code terms_acceptances}); solo inserta: es evidencia.
 * {@code source} (V24): {@code REGISTER} (alta), {@code INVITATION} (al aceptar una invitación, sin usuario) o
 * {@code UPDATE} (la persona acepta la versión nueva, bajo RLS: {@code terms_own_insert}).
 */
public interface TermsAcceptanceRepository {
    void insert(UUID profileId, UUID organizationId, String termsVersion, OffsetDateTime acceptedAt, String source);

    default void insert(UUID profileId, UUID organizationId, String termsVersion, OffsetDateTime acceptedAt) {
        insert(profileId, organizationId, termsVersion, acceptedAt, "REGISTER");
    }

    /** true si la persona aceptó esa versión alguna vez. */
    boolean hasAccepted(UUID profileId, String termsVersion);
}
