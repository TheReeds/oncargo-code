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
import pe.logyx.shipment.application.DriverShipmentScope;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.TrackStopsUseCase;
import pe.logyx.shipment.application.ShipmentLifecycleUseCase;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.CancelShipment;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.ChangeVehicle;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.AssignDriver;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.ShipmentView;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class ShipmentResource {

    @Inject ShipmentQueries queries;
    @Inject AssignDriverUseCase assignDriver;
    @Inject TrackStopsUseCase tracking;
    @Inject ShipmentLifecycleUseCase lifecycle;

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
    @Operation(summary = "Asigna o releva el conductor (motivo obligatorio si ya se recogió; sin envíos solapados, "
            + "LOGYX-SHP-004)")
    public ApiResponse<ShipmentView> assign(@PathParam("id") UUID id, @Valid AssignDriver req) {
        return ApiResponse.of(ShipmentView.of(assignDriver.assign(id, req.driverId(), req.reason())));
    }

    @PUT
    @Path("/shipments/{id}/vehicle")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "Cambia el vehículo del envío (RN02: kg, tipo y m³; sin envíos solapados, LOGYX-CAP-005; "
            + "motivo obligatorio si ya se recogió)")
    public ApiResponse<ShipmentView> changeVehicle(@PathParam("id") UUID id, @Valid ChangeVehicle req) {
        return ApiResponse.of(ShipmentView.of(lifecycle.changeVehicle(id, req.vehicleId(), req.reason())));
    }

    @POST
    @Path("/shipments/{id}/cancel")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    @Operation(summary = "Cancela el envío con motivo: las partes antes del recojo (LOGYX-SHP-003), el operador también "
            + "en tránsito. La solicitud queda cancelada y se avisa a la contraparte")
    public ApiResponse<ShipmentView> cancel(@PathParam("id") UUID id, @Valid @NotNull CancelShipment req) {
        return ApiResponse.of(ShipmentView.of(lifecycle.cancel(id, req.reason())));
    }

    // ----- App del conductor
    @GET
    @Path("/driver/shipments")
    @RolesAllowed(Roles.DRIVER)
    @Operation(summary = "Mis envíos como conductor: activos (por omisión), historial o todos (F22)")
    public ApiResponse<List<ShipmentView>> driverShipments(
            @Parameter(description = "ACTIVE (por omisión: pendientes y en tránsito), HISTORY (entregados y cancelados, "
                    + "recientes primero) o ALL (activos y luego historial).") @QueryParam("scope") String scope) {
        return ApiResponse.of(ShipmentView.of(queries.driverShipments(DriverShipmentScope.parse(scope))));
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
