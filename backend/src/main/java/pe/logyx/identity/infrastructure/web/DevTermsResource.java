package pe.logyx.identity.infrastructure.web;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;

import java.util.Map;

/**
 * Solo desarrollo y e2e (V24, 2.17): olvida las aceptaciones de los Términos de una cuenta de prueba para ver el diálogo
 * «Actualizamos los Términos» sin cambiar {@code logyx.terms.version}. Misma condición de build que el buzón dev
 * ({@code logyx.dev-mailbox=true}): nunca existe en producción. Fuera del contrato OpenAPI. No toca las cuentas demo.
 */
@IfBuildProperty(name = "logyx.dev-mailbox", stringValue = "true")
@Path("/api/v1")
public class DevTermsResource {

    @Inject SystemTx system;
    @Inject EntityManager em;

    @POST
    @Path("/dev/terms/forget")
    @PermitAll
    @Operation(hidden = true)
    // SystemTx: herramienta de desarrollo sin usuario; terms_acceptances no admite DELETE para logyx_app.
    public ApiResponse<Map<String, Integer>> forget(@QueryParam("email") String email) {
        if (email == null || email.isBlank() || email.trim().toLowerCase().endsWith("@logyx.pe")) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el correo de una cuenta de prueba (no demo)", "email");
        }
        int n = system.call(() -> em.createNativeQuery("DELETE FROM terms_acceptances WHERE profile_id IN "
                + "(SELECT id FROM profiles WHERE email = ?1)").setParameter(1, email.trim().toLowerCase()).executeUpdate());
        return ApiResponse.of(Map.of("deleted", n));
    }
}
