package pe.logyx.fleet;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
@RolesAllowed(Roles.CARRIER)
public class FleetResource {

    public record StatusChange(@NotNull Vehicle.Status status) {}

    @Inject FleetService fleet;

    @GET
    @Path("/vehicles")
    public ApiResponse<List<FleetService.VehicleView>> vehicles() {
        return ApiResponse.of(fleet.vehicles());
    }

    @POST
    @Path("/vehicles")
    @ResponseStatus(201)
    public ApiResponse<FleetService.VehicleView> addVehicle(@Valid FleetService.VehicleRequest req) {
        return ApiResponse.of(fleet.addVehicle(req));
    }

    @PATCH
    @Path("/vehicles/{id}/status")
    public ApiResponse<FleetService.VehicleView> status(@PathParam("id") UUID id, @Valid StatusChange req) {
        return ApiResponse.of(fleet.setVehicleStatus(id, req.status()));
    }

    @GET
    @Path("/drivers")
    public ApiResponse<List<FleetService.DriverView>> drivers() {
        return ApiResponse.of(fleet.drivers());
    }

    @POST
    @Path("/drivers")
    @ResponseStatus(201)
    public ApiResponse<FleetService.DriverView> addDriver(@Valid FleetService.DriverRequest req) {
        return ApiResponse.of(fleet.addDriver(req));
    }
}
