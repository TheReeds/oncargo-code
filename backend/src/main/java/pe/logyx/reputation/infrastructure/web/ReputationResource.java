package pe.logyx.reputation.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.reputation.application.ReputationQueries;
import pe.logyx.reputation.application.ReviewService;
import pe.logyx.reputation.infrastructure.web.ReputationDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.UUID;

@Path("/api/v1")
public class ReputationResource {

    @Inject ReviewService reviews;
    @Inject ReputationQueries queries;

    @POST
    @Path("/shipments/{id}/reviews")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @ResponseStatus(201)
    @Operation(summary = "Califica a la otra parte de un envío entregado con foto de evidencia (RN06)")
    public ApiResponse<ReviewView> submit(@PathParam("id") UUID shipmentId, @Valid SubmitReview req) {
        return ApiResponse.of(ReviewView.of(reviews.submit(shipmentId, req.punctuality(), req.cargoCondition(),
                req.communication(), req.comment())));
    }

    @GET
    @Path("/shipments/{id}/reviews")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    @Operation(summary = "Calificaciones del envío y si puedo calificarlo (RF45)")
    public ApiResponse<ShipmentReviewsView> forShipment(@PathParam("id") UUID shipmentId) {
        return ApiResponse.of(ShipmentReviewsView.of(queries.forShipment(shipmentId)));
    }
}
