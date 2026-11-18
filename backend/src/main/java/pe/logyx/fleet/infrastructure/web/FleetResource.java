package pe.logyx.fleet.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.infrastructure.web.FleetDtos.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.security.OrgAdminOnly;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
@RolesAllowed(Roles.CARRIER)
public class FleetResource {

    @Inject FleetService fleet;

    @GET
    @Path("/vehicles")
    public ApiResponse<List<VehicleView>> vehicles() {
        return ApiResponse.of(fleet.vehicles().stream().map(VehicleView::of).toList());
    }

    @POST
    @Path("/vehicles")
    @ResponseStatus(201)
    public ApiResponse<VehicleView> addVehicle(@Valid VehicleRequest req) {
        return ApiResponse.of(VehicleView.of(fleet.addVehicle(req.toCommand())));
    }

    @PATCH
    @Path("/vehicles/{id}/status")
    public ApiResponse<VehicleView> status(@PathParam("id") UUID id, @Valid StatusChange req) {
        return ApiResponse.of(VehicleView.of(fleet.setVehicleStatus(id, req.status())));
    }

    @GET
    @Path("/drivers")
    public ApiResponse<List<DriverView>> drivers() {
        return ApiResponse.of(fleet.drivers().stream().map(DriverView::of).toList());
    }

    @POST
    @Path("/drivers")
    @ResponseStatus(201)
    @OrgAdminOnly
    @Operation(summary = "Alta de conductor", description = "Solo ADMIN (403 LOGYX-AUTH-008).")
    public ApiResponse<DriverView> addDriver(@Valid DriverRequest req) {
        return ApiResponse.of(DriverView.of(fleet.addDriver(req.toCommand())));
    }

    @POST
    @Path("/drivers/{id}/deactivate")
    @OrgAdminOnly
    @Operation(summary = "Desactivar la cuenta del conductor", description = "Pierde el acceso de inmediato y no se "
            + "le puede asignar envíos (409 LOGYX-FLT-003). Solo ADMIN.")
    public ApiResponse<DriverView> deactivateDriver(@PathParam("id") UUID id) {
        return ApiResponse.of(DriverView.of(fleet.setDriverActive(id, false)));
    }

    @POST
    @Path("/drivers/{id}/reactivate")
    @OrgAdminOnly
    @Operation(summary = "Reactivar la cuenta del conductor", description = "Solo ADMIN.")
    public ApiResponse<DriverView> reactivateDriver(@PathParam("id") UUID id) {
        return ApiResponse.of(DriverView.of(fleet.setDriverActive(id, true)));
    }

    @POST
    @Path("/drivers/{id}/reset-password")
    @OrgAdminOnly
    @Operation(summary = "Nueva contraseña para el conductor", description = "El ADMIN la define y se la comunica; "
            + "se cierran las sesiones del conductor. Solo ADMIN.")
    public ApiResponse<DriverView> resetDriverPassword(@PathParam("id") UUID id, @Valid DriverPasswordReset req) {
        return ApiResponse.of(DriverView.of(fleet.resetDriverPassword(id, req.newPassword())));
    }
}
