package pe.logyx.identity.infrastructure.web;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.identity.application.PublicAccessRequestService;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.security.AuthRateLimits;


/** «Pedir acceso a la organización» desde el registro (2.15). Público (sin JWT), con límite por IP. */
@Path("/api/v1")
public class PublicAccessRequestResource {

    @Inject PublicAccessRequestService service;
    @Inject AuthRateLimits limits;
    @Inject HttpServerRequest http;

    /** {@code ruc}: el que dio LOGYX-ORG-002 al registrarse. */
    public record OrganizationAccessRequest(@NotBlank @Pattern(regexp = "\\d{11}", message = "RUC de 11 dígitos") String ruc,
                                            @NotBlank @Size(min = 2, max = 150) String fullName,
                                            @NotBlank @Email @Size(max = 254) String email) {}

    /** Siempre {@code accepted = true}. */
    public record OrganizationAccessRequestAccepted(boolean accepted) {}

    @POST
    @Path("/public/organization-access-requests")
    @PermitAll
    @ResponseStatus(202)
    @Operation(summary = "Pide unirse a una organización ya registrada (tras LOGYX-ORG-002): avisa por correo a sus ADMIN",
            description = "Sin sesión. Responde siempre 202 con el mismo cuerpo (no revela si la organización existe ni "
                    + "a quién se avisó). Límite por IP (429 LOGYX-AUTH-429) y un aviso por organización cada 10 min.")
    public ApiResponse<OrganizationAccessRequestAccepted> request(@Valid @NotNull OrganizationAccessRequest req) {
        var addr = http.remoteAddress();
        limits.beforePublicAccessRequest(addr == null ? "desconocida" : addr.hostAddress());
        service.request(req.ruc(), req.fullName(), req.email());
        return ApiResponse.of(new OrganizationAccessRequestAccepted(true));
    }
}
