package pe.logyx.identity.infrastructure.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.identity.application.LegalTerms;
import pe.logyx.shared.api.ApiResponse;

/** Textos legales vigentes (sin JWT): la web muestra la versión y la envía al registrarse. */
@Path("/api/v1/public/legal")
public class LegalResource {

    /** {@code termsStatus}: {@code DRAFT} (borrador pendiente de revisión legal) o {@code PUBLISHED}. */
    public record LegalInfo(String termsVersion, String termsStatus) {}

    @Inject LegalTerms terms;

    @GET
    @Operation(summary = "Versión vigente de los Términos y condiciones y su estado",
            description = "Sin JWT. El registro (POST /auth/register) exige acceptTerms=true y acepta termsVersion; "
                    + "si no es la vigente responde 409 LOGYX-AUTH-012.")
    public ApiResponse<LegalInfo> legal() {
        return ApiResponse.of(new LegalInfo(terms.version(), terms.status()));
    }
}
