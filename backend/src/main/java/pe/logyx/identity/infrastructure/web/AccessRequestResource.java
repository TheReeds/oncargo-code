package pe.logyx.identity.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.identity.application.AccessRequestService;
import pe.logyx.identity.domain.OrgPermission;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

/**
 * «Pedir acceso» al ADMIN (roadmap v2, 1.7/1.15). Misma raíz exacta que {@code OrganizationResource} (Quarkus REST no
 * combina raíces que se solapan).
 */
@Path("/api/v1/organizations")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
public class AccessRequestResource {

    public record CreateAccessRequest(@NotNull OrgPermission permission, @Size(max = 300) String reason) {}

    public record AccessRequestView(OrgPermission permission, int notifiedAdmins) {}

    @Inject AccessRequestService service;

    @POST
    @Path("/me/access-requests")
    @ResponseStatus(202)
    @Operation(summary = "Pedir un permiso a los administradores de mi organización",
            description = "Cualquier rol, también Lector. Avisa (in-app y, según sus preferencias, por correo) a cada "
                    + "ADMIN activo para que cambie tu rol; queda en la auditoría. 409 LOGYX-ACC-001 si tu rol ya tiene "
                    + "el permiso; 429 LOGYX-ACC-002 si ya lo pediste en los últimos 10 minutos; 409 LOGYX-ACC-003 si "
                    + "no hay otro ADMIN activo.")
    public ApiResponse<AccessRequestView> request(@Valid @NotNull CreateAccessRequest req) {
        var r = service.request(req.permission(), req.reason());
        return ApiResponse.of(new AccessRequestView(r.permission(), r.notifiedAdmins()));
    }
}
