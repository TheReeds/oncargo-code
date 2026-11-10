package pe.logyx.shipment;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shipment.ShipmentService.ShipmentView;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class ShipmentResource {

    public record AssignDriver(@NotNull UUID driverId) {}

    @Inject ShipmentService shipments;

    @GET
    @Path("/shipments")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    public ApiResponse<List<ShipmentView>> mine() {
        return ApiResponse.of(shipments.mine());
    }

    @GET
    @Path("/shipments/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    public ApiResponse<ShipmentView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(shipments.detail(id));
    }

    @PUT
    @Path("/shipments/{id}/driver")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<ShipmentView> assign(@PathParam("id") UUID id, @Valid AssignDriver req) {
        return ApiResponse.of(shipments.assignDriver(id, req.driverId()));
    }

    // ----- App del conductor
    @GET
    @Path("/driver/shipments")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<List<ShipmentView>> driverShipments() {
        return ApiResponse.of(shipments.driverShipments());
    }

    @POST
    @Path("/shipments/{id}/stops/{stopId}/arrive")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<ShipmentView> arrive(@PathParam("id") UUID id, @PathParam("stopId") UUID stopId) {
        return ApiResponse.of(shipments.arrive(id, stopId));
    }

    @POST
    @Path("/shipments/{id}/stops/{stopId}/complete")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<ShipmentView> complete(@PathParam("id") UUID id, @PathParam("stopId") UUID stopId) {
        return ApiResponse.of(shipments.complete(id, stopId));
    }
}
