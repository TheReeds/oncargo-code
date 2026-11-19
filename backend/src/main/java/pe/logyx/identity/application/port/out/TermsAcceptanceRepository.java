package pe.logyx.identity.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Aceptaciones de los Términos y condiciones (V18, {@code terms_acceptances}); solo inserta: es evidencia. */
public interface TermsAcceptanceRepository {
    void insert(UUID profileId, UUID organizationId, String termsVersion, OffsetDateTime acceptedAt);
}
