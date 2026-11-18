package pe.logyx.identity.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.identity.application.OrganizationUserService;
import pe.logyx.identity.infrastructure.web.OrganizationDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.security.OrgAdminOnly;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

/**
 * Usuarios de mi organización (F03). Solo ADMIN (403 LOGYX-AUTH-008 para OPS/VIEWER). La organización sale del JWT.
 * Misma raíz exacta que {@code OrganizationResource} (Quarkus REST no combina raíces que se solapan).
 */
@Path("/api/v1/organizations")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
@OrgAdminOnly
public class OrganizationUsersResource {

    @Inject OrganizationUserService users;

    @GET
    @Path("/me/users")
    @Operation(summary = "Usuarios de mi organización", description = "Sin conductores (están en Flota).")
    public ApiResponse<List<OrganizationUser>> list() {
        return ApiResponse.of(users.list().stream().map(OrganizationUser::of).toList());
    }

    @POST
    @Path("/me/users")
    @ResponseStatus(201)
    @Operation(summary = "Invitar usuario", description = "Crea el usuario sin contraseña y le envía un enlace de "
            + "invitación (72 h) que se canjea con POST /auth/reset-password. 409 LOGYX-AUTH-002 (email ya registrado), "
            + "400 LOGYX-USR-004 (rol DRIVER).")
    public ApiResponse<OrganizationUser> invite(@Valid InviteUserRequest req) {
        return ApiResponse.of(OrganizationUser.of(users.invite(req.email(), req.fullName(), req.role())));
    }

    @POST
    @Path("/me/users/{id}/resend-invitation")
    @Operation(summary = "Reenviar invitación", description = "Nuevo enlace; el anterior deja de valer. "
            + "409 LOGYX-USR-003 si ya la aceptó.")
    public ApiResponse<OrganizationUser> resendInvitation(@PathParam("id") UUID id) {
        return ApiResponse.of(OrganizationUser.of(users.resendInvitation(id)));
    }

    @PATCH
    @Path("/me/users/{id}")
    @Operation(summary = "Cambiar rol", description = "Cierra las sesiones del usuario. 409 LOGYX-USR-001 (último "
            + "ADMIN activo), LOGYX-USR-005 (conductor). Cambiar el propio rol cierra tu sesión.")
    public ApiResponse<OrganizationUser> changeRole(@PathParam("id") UUID id, @Valid ChangeUserRoleRequest req) {
        return ApiResponse.of(OrganizationUser.of(users.changeRole(id, req.role())));
    }

    @POST
    @Path("/me/users/{id}/deactivate")
    @Operation(summary = "Desactivar usuario", description = "Pierde el acceso de inmediato. 409 LOGYX-USR-001 "
            + "(último ADMIN activo), LOGYX-USR-002 (tú mismo).")
    public ApiResponse<OrganizationUser> deactivate(@PathParam("id") UUID id) {
        return ApiResponse.of(OrganizationUser.of(users.deactivate(id)));
    }

    @POST
    @Path("/me/users/{id}/reactivate")
    @Operation(summary = "Reactivar usuario")
    public ApiResponse<OrganizationUser> reactivate(@PathParam("id") UUID id) {
        return ApiResponse.of(OrganizationUser.of(users.reactivate(id)));
    }
}
