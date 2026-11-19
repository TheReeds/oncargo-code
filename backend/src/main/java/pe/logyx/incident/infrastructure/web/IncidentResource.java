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
import jakarta.validation.constraints.NotNull;

import java.util.*;

@Path("/api/v1")
public class IncidentResource {

    @Inject IncidentService service;
    @Inject IncidentQueries queries;

    @POST
    @Path("/shipments/{id}/incidents")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER})
    @ResponseStatus(201)
    @Operation(summary = "Reporta una incidencia; si su categoría congela (blocking), el avance de paradas queda en pausa "
            + "hasta que el operador la resuelva (RN11)",
            description = "V23 (2.7): DELAY, ROAD_BLOCKED y PAYMENT_DISPUTE son informativas (no congelan). Una activa que "
                    + "congela por envío y una activa por categoría (LOGYX-INC-001). Adjuntos: documentos de este envío "
                    + "(LOGYX-INC-011). Con la cabecera Idempotency-Key un reintento devuelve la ya creada.")
    public ApiResponse<IncidentView> report(@PathParam("id") UUID shipmentId,
                                            @org.eclipse.microprofile.openapi.annotations.parameters.Parameter(
                                                    description = "Clave de idempotencia (UUID recomendado), 24 h.")
                                            @HeaderParam("Idempotency-Key") String idempotencyKey,
                                            @Valid ReportIncident req) {
        var cmd = new pe.logyx.incident.application.ReportIncidentCommand(req.category(), req.description(),
                req.severity(), req.claimedAmount(), req.attachmentIds(), req.latitude(), req.longitude(),
                req.occurredAt(), req.adjustmentId(),
                pe.logyx.shared.idempotency.IdempotencyKeys.validate(idempotencyKey));
        return ApiResponse.of(IncidentView.of(service.report(shipmentId, cmd)));
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
    @Operation(summary = "Comenta una incidencia. internal=true: nota interna del operador (LOGYX-INC-009 si la "
            + "intenta una parte); las partes no la ven")
    public ApiResponse<IncidentView> comment(@PathParam("id") UUID id, @Valid AddIncidentComment req) {
        service.comment(id, req.body(), Boolean.TRUE.equals(req.internal()));
        return ApiResponse.of(IncidentView.of(queries.detail(id)));
    }

    @POST
    @Path("/incidents/{id}/withdraw")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER})
    @Operation(summary = "Quien la reportó retira una incidencia informativa activa (2.7, LOGYX-INC-007)")
    public ApiResponse<IncidentView> withdraw(@PathParam("id") UUID id) {
        return ApiResponse.of(IncidentView.of(service.withdraw(id)));
    }

    @POST
    @Path("/incidents/{id}/reopen")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Operador: reabre una incidencia resuelta o cerrada con motivo (2.7, LOGYX-INC-008); vuelve a "
            + "IN_REVIEW con un SLA nuevo y, si congela, vuelve a detener el envío en curso")
    public ApiResponse<IncidentView> reopen(@PathParam("id") UUID id, @Valid @NotNull ReopenIncident req) {
        return ApiResponse.of(IncidentView.of(service.reopen(id, req.reason())));
    }

    @PUT
    @Path("/incidents/{id}/assignee")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Operador: toma la incidencia (sin cuerpo o assigneeProfileId nulo), la asigna a otra persona "
            + "del operador (LOGYX-INC-012) o la deja sin responsable (unassign)")
    public ApiResponse<IncidentView> assign(@PathParam("id") UUID id, @Valid AssignIncident req) {
        return ApiResponse.of(IncidentView.of(service.assign(id, req == null ? null : req.assigneeProfileId(),
                req != null && Boolean.TRUE.equals(req.unassign()))));
    }

    @POST
    @Path("/incidents/{id}/status")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Operador: cambia el estado (IN_REVIEW, RESOLVED, CLOSED). Resolver o cerrar una activa exige "
            + "motivo (LOGYX-INC-003) y el estado final del envío RESUME/DELIVERED/CANCELLED (RF55, LOGYX-INC-006)")
    public ApiResponse<IncidentView> changeStatus(@PathParam("id") UUID id, @Valid ChangeIncidentStatus req) {
        IncidentSummary s = service.changeStatus(id, req.status(), req.resolutionNotes(), req.shipmentOutcome());
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
