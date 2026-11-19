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
    @Path("/vehicles/{id}")
    @Operation(summary = "Corrige tipo, kg y m³ de un vehículo (la placa no cambia)",
            description = "Con envíos activos, ofertas pendientes o viajes publicados solo se admite el volumen "
                    + "(409 LOGYX-FLT-004). Un vehículo archivado no se edita.")
    public ApiResponse<VehicleView> update(@PathParam("id") UUID id, @Valid VehiclePatch req) {
        return ApiResponse.of(VehicleView.of(fleet.updateVehicle(id, req.toCommand())));
    }

    @PUT
    @Path("/vehicles/{id}/config")
    @Operation(summary = "Equipamiento y tipos de carga aceptados por el vehículo (2.4)",
            description = "Reemplaza ambas listas. acceptedCargoTypes restringe la matriz RN02 de ese vehículo, nunca la "
                    + "amplía (400 LOGYX-FLT-009); vacío = todo lo que la matriz permite. Ofertar con un tipo no aceptado "
                    + "da 400 LOGYX-CAP-003.")
    public ApiResponse<VehicleView> configure(@PathParam("id") UUID id, @Valid VehicleConfig req) {
        return ApiResponse.of(VehicleView.of(fleet.configureVehicle(id, req.equipment(), req.acceptedCargoTypes())));
    }

    @POST
    @Path("/vehicles/{id}/archive")
    @Operation(summary = "Da de baja (archiva) un vehículo",
            description = "Sin envíos activos, ofertas pendientes ni viajes publicados (409 LOGYX-FLT-004). Un vehículo "
                    + "archivado no aparece en las listas ni puede ofertar; la placa sigue registrada.")
    public ApiResponse<VehicleView> archive(@PathParam("id") UUID id) {
        return ApiResponse.of(VehicleView.of(fleet.archiveVehicle(id)));
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

    @PATCH
    @Path("/drivers/{id}")
    @OrgAdminOnly
    @Operation(summary = "Categoría de licencia y DNI del conductor (2.4)", description = "Solo ADMIN. El vencimiento de "
            + "la licencia se registra al subir el documento DRIVER_LICENSE.")
    public ApiResponse<DriverView> patchDriver(@PathParam("id") UUID id, @Valid DriverPatch req) {
        return ApiResponse.of(DriverView.of(fleet.updateDriverCredentials(id, req.licenseCategory(), req.dni())));
    }

    @POST
    @Path("/drivers/{id}/deactivate")
    @OrgAdminOnly
    @Operation(summary = "Desactivar la cuenta del conductor", description = "Pierde el acceso de inmediato y no se "
            + "le puede asignar envíos (409 LOGYX-FLT-003). Con envíos activos o por salir no se desactiva "
            + "(409 LOGYX-FLT-006: reasigna primero). Solo ADMIN.")
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
