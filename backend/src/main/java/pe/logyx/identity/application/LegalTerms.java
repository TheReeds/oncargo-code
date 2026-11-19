package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.shared.domain.BusinessException;

/**
 * Versión vigente de los Términos y condiciones (decisión del dueño, 2026-11-17). Al registrarse se acepta una versión
 * concreta y se guarda con su fecha ({@code terms_acceptances}), igual que el consentimiento de la Ley 29733. Cambiar
 * {@code logyx.terms.version} cuando el texto cambie; el estado ({@code DRAFT} hasta la revisión legal) lo muestra la web.
 */
@ApplicationScoped
public class LegalTerms {

    @ConfigProperty(name = "logyx.terms.version", defaultValue = "2026-11-17-borrador") String version;
    @ConfigProperty(name = "logyx.terms.status", defaultValue = "DRAFT") String status;

    public String version() { return version; }

    /** {@code DRAFT} (pendiente de revisión legal) o {@code PUBLISHED}. */
    public String status() { return status; }

    /**
     * La versión que dice haber leído el cliente debe ser la vigente; si omite la versión se asume la vigente.
     * {@code LOGYX-AUTH-012} (409, {@code termsVersion}) si el texto cambió mientras llenaba el formulario.
     */
    public String ensureCurrent(String accepted) {
        if (accepted == null || accepted.isBlank()) return version;
        if (!version.equals(accepted.trim())) {
            throw BusinessException.conflict("LOGYX-AUTH-012",
                    "Los Términos y condiciones cambiaron. Recarga la página, léelos y vuelve a aceptarlos",
                    "termsVersion");
        }
        return version;
    }
}
