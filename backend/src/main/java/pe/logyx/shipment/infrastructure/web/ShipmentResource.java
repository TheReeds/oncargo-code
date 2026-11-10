package pe.logyx.shipment.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shipment.application.AssignDriverUseCase;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.TrackStopsUseCase;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.AssignDriver;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.ShipmentView;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class ShipmentResource {

    @Inject ShipmentQueries queries;
    @Inject AssignDriverUseCase assignDriver;
    @Inject TrackStopsUseCase tracking;

    @GET
    @Path("/shipments")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    public ApiResponse<List<ShipmentView>> mine(
            @Parameter(description = "Página 0-based. Si se omite page y size, se devuelven todos.")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20 cuando se pagina).")
            @QueryParam("size") Integer size) {
        if (page == null && size == null) return ApiResponse.of(ShipmentView.of(queries.mine()));
        return ApiResponse.of(queries.mine(PageRequest.of(page, size)).map(ShipmentView::of));
    }

    @GET
    @Path("/shipments/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    public ApiResponse<ShipmentView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(ShipmentView.of(queries.detail(id)));
    }

    @PUT
    @Path("/shipments/{id}/driver")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<ShipmentView> assign(@PathParam("id") UUID id, @Valid AssignDriver req) {
        return ApiResponse.of(ShipmentView.of(assignDriver.assign(id, req.driverId())));
    }

    // ----- App del conductor
    @GET
    @Path("/driver/shipments")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<List<ShipmentView>> driverShipments() {
        return ApiResponse.of(ShipmentView.of(queries.driverShipments()));
    }

    @GET
    @Path("/driver/shipments/active")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<ShipmentView> driverActive() {
        return ApiResponse.of(ShipmentView.of(queries.activeForDriver()));
    }

    @POST
    @Path("/shipments/{id}/stops/{stopId}/arrive")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<ShipmentView> arrive(@PathParam("id") UUID id, @PathParam("stopId") UUID stopId) {
        return ApiResponse.of(ShipmentView.of(tracking.arrive(id, stopId)));
    }

    @POST
    @Path("/shipments/{id}/stops/{stopId}/complete")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<ShipmentView> complete(@PathParam("id") UUID id, @PathParam("stopId") UUID stopId) {
        return ApiResponse.of(ShipmentView.of(tracking.complete(id, stopId)));
    }
}
