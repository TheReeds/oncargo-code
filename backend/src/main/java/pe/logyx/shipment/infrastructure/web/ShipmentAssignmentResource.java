package pe.logyx.shipment.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shipment.application.AssignResourcesUseCase;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.ShipmentView;

import java.util.UUID;

/** Asignar conductor y vehículo en un solo paso (roadmap 2.13). */
@Path("/api/v1")
public class ShipmentAssignmentResource {

    @Inject AssignResourcesUseCase assign;

    /** Al menos uno de los dos. {@code reason}: obligatorio si el envío ya se recogió (F06). */
    public record AssignResources(UUID driverId, UUID vehicleId, @Size(max = 300) String reason) {}

    @PUT
    @Path("/shipments/{id}/assignment")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "2.13: asigna conductor y vehículo en un solo paso (atómico)",
            description = "Aplica las reglas de PUT /shipments/{id}/vehicle y PUT /shipments/{id}/driver juntas: RN02, sin "
                    + "solapes (CAP-005, SHP-004) y motivo si ya se recogió. Si algo falla no queda ningún cambio. "
                    + "Las sugerencias salen de GET /fleet/assignment-suggestions.")
    public ApiResponse<ShipmentView> assignment(@PathParam("id") UUID id, @Valid AssignResources req) {
        return ApiResponse.of(ShipmentView.of(assign.assign(id, req.driverId(), req.vehicleId(), req.reason())));
    }
}
