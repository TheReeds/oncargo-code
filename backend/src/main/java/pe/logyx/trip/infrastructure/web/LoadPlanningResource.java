package pe.logyx.trip.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.trip.infrastructure.web.TripDtos.AcceptSmartLoadRequest;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.trip.application.ReturnLoadService;
import pe.logyx.trip.application.SmartLoadService;
import pe.logyx.trip.domain.model.SmartLoadStatus;
import pe.logyx.trip.infrastructure.web.TripDtos.ReturnLoadView;
import pe.logyx.trip.infrastructure.web.TripDtos.SmartLoadView;

import java.util.List;
import java.util.UUID;

/** Cargas de retorno (RF61–RF63) y Smart Load Planner (OE07) del transportista. */
@Path("/api/v1")
public class LoadPlanningResource {

    @Inject ReturnLoadService returnLoads;
    @Inject SmartLoadService smartLoads;

    @GET
    @Path("/return-loads")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<ReturnLoadView>> returnLoads() {
        return ApiResponse.of(returnLoads.forCurrentCarrier().stream().map(ReturnLoadView::of).toList());
    }

    @GET
    @Path("/smart-loads")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<SmartLoadView>> list(
            @Parameter(description = "Filtra por estado (PENDING, ACCEPTED, REJECTED, EXPIRED).")
            @QueryParam("status") String status) {
        SmartLoadStatus s = null;
        if (status != null && !status.isBlank()) {
            try {
                s = SmartLoadStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Estado inválido: " + status, "status");
            }
        }
        return ApiResponse.of(smartLoads.list(s).stream().map(SmartLoadView::of).toList());
    }

    /** Genera sugerencias nuevas ahora; devuelve solo las creadas (lista vacía si no hay combinaciones nuevas). */
    @POST
    @Path("/smart-loads/generate")
    @RolesAllowed(Roles.CARRIER)
    @ResponseStatus(201)
    public ApiResponse<List<SmartLoadView>> generate() {
        return ApiResponse.of(smartLoads.generateForCurrentCarrier().stream().map(SmartLoadView::of).toList());
    }

    @POST
    @Path("/smart-loads/{id}/accept")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "RF32/F12: acepta el plan y oferta en lote (una oferta por carga con el vehículo del plan, al "
            + "precio sugerido o al indicado). Todo o nada (LOGYX-TRP-011); no adjudica: decide cada PYME")
    public ApiResponse<SmartLoadView> accept(@PathParam("id") UUID id, @Valid AcceptSmartLoadRequest req) {
        java.util.Map<UUID, java.math.BigDecimal> amounts = new java.util.HashMap<>();
        if (req != null && req.items() != null) req.items().forEach(i -> amounts.put(i.requestId(), i.amount()));
        var a = smartLoads.accept(id, amounts, req == null ? null : req.notes());
        return ApiResponse.of(SmartLoadView.of(a));
    }

    @POST
    @Path("/smart-loads/{id}/reject")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<SmartLoadView> reject(@PathParam("id") UUID id) {
        return ApiResponse.of(SmartLoadView.of(smartLoads.reject(id)));
    }
}
