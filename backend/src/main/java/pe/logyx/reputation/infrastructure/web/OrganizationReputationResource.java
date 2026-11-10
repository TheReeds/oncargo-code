package pe.logyx.reputation.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.reputation.application.ReputationQueries;
import pe.logyx.reputation.infrastructure.web.ReputationDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.PageRequest;

import java.util.List;
import java.util.UUID;

/**
 * Reputación pública por organización (RF44). Misma raíz que OrganizationResource (identity): en Quarkus REST
 * dos recursos con raíces distintas que se solapan ("/api/v1" vs "/api/v1/organizations") no se combinan.
 */
@Path("/api/v1/organizations")
@Authenticated
public class OrganizationReputationResource {

    @Inject ReputationQueries queries;

    @GET
    @Path("/{id}/reputation")
    @Operation(summary = "Perfil público de reputación: Trust Score (RN09), promedios y últimas calificaciones")
    public ApiResponse<ReputationView> profile(@PathParam("id") UUID orgId) {
        return ApiResponse.of(ReputationView.of(queries.profile(orgId)));
    }

    @GET
    @Path("/{id}/reviews")
    @Operation(summary = "Calificaciones recibidas por la organización, recientes primero")
    public ApiResponse<List<ReviewView>> received(
            @PathParam("id") UUID orgId,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        return ApiResponse.of(queries.received(orgId, PageRequest.of(page, size)).map(ReviewView::of));
    }
}
