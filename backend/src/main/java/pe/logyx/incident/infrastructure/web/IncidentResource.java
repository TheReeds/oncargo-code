package pe.logyx.incident.infrastructure.web;

import pe.logyx.incident.domain.model.IncidentStatus;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.incident.application.IncidentQueries;
import pe.logyx.incident.application.IncidentService;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.infrastructure.web.IncidentDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.Roles;

import java.util.*;

@Path("/api/v1")
public class IncidentResource {

    @Inject IncidentService service;
    @Inject IncidentQueries queries;

    @POST
    @Path("/shipments/{id}/incidents")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER})
    @ResponseStatus(201)
    @Operation(summary = "Reporta una incidencia; congela el avance de paradas hasta que el operador la resuelva (RN11)")
    public ApiResponse<IncidentView> report(@PathParam("id") UUID shipmentId, @Valid ReportIncident req) {
        return ApiResponse.of(IncidentView.of(service.report(shipmentId, req.category(), req.description())));
    }

    @GET
    @Path("/shipments/{id}/incidents")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    public ApiResponse<List<IncidentView>> byShipment(@PathParam("id") UUID shipmentId) {
        return ApiResponse.of(queries.byShipment(shipmentId).stream().map(IncidentView::of).toList());
    }

    @GET
    @Path("/incidents")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    @Operation(summary = "Incidencias de mis envíos (operador: todas), recientes primero")
    public ApiResponse<List<IncidentView>> search(
            @Parameter(description = "Estados separados por coma (OPEN,IN_REVIEW,RESOLVED,CLOSED).")
            @QueryParam("status") String status,
            @Parameter(description = "true = solo activas (OPEN o IN_REVIEW).") @QueryParam("active") Boolean active,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        Set<IncidentStatus> statuses = new LinkedHashSet<>(parseStatuses(status));
        if (Boolean.TRUE.equals(active)) {
            if (statuses.isEmpty()) statuses.addAll(List.of(IncidentStatus.OPEN, IncidentStatus.IN_REVIEW));
            else statuses.removeIf(s -> !s.isActive());
            if (statuses.isEmpty()) return ApiResponse.of(new Paged<IncidentView>(List.of(), PageRequest.of(page, size), 0));
        }
        return ApiResponse.of(queries.search(statuses, PageRequest.of(page, size)).map(IncidentView::of));
    }

    @GET
    @Path("/incidents/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    public ApiResponse<IncidentView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(IncidentView.of(queries.detail(id)));
    }

    @POST
    @Path("/incidents/{id}/comments")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    @ResponseStatus(201)
    public ApiResponse<IncidentView> comment(@PathParam("id") UUID id, @Valid AddIncidentComment req) {
        service.comment(id, req.body());
        return ApiResponse.of(IncidentView.of(queries.detail(id)));
    }

    @POST
    @Path("/incidents/{id}/status")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Operador: cambia el estado (IN_REVIEW, RESOLVED con notas, CLOSED)")
    public ApiResponse<IncidentView> changeStatus(@PathParam("id") UUID id, @Valid ChangeIncidentStatus req) {
        IncidentSummary s = service.changeStatus(id, req.status(), req.resolutionNotes());
        return ApiResponse.of(IncidentView.of(s));
    }

    private static List<IncidentStatus> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<IncidentStatus> out = new ArrayList<>();
        for (String p : raw.split(",")) {
            String v = p.trim();
            if (v.isEmpty()) continue;
            out.add(Arrays.stream(IncidentStatus.values()).filter(s -> s.name().equalsIgnoreCase(v)).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Estado inválido: " + v, "status")));
        }
        return out;
    }
}
