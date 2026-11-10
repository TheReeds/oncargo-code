package pe.logyx.operator.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.operator.application.OperatorService;
import pe.logyx.operator.infrastructure.web.OperatorDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

/** Panel del operador LOGYX (cuentas OPERATOR, sin registro público). La gestión de incidencias está en /incidents. */
@Path("/api/v1/operator")
@RolesAllowed(Roles.OPERATOR)
public class OperatorResource {

    @Inject OperatorService service;

    @GET
    @Path("/sla-alerts")
    @Operation(summary = "Alertas SLA: sin ofertas > 24 h (RN10), subastas por vencer, envíos con incidencia (RN11)")
    public ApiResponse<OperatorAlertsView> alerts() {
        return ApiResponse.of(OperatorAlertsView.of(service.alerts()));
    }

    @GET
    @Path("/metrics")
    @Operation(summary = "Métricas globales de la plataforma")
    public ApiResponse<OperatorMetricsView> metrics() {
        return ApiResponse.of(OperatorMetricsView.of(service.metrics()));
    }

    @GET
    @Path("/requests/{id}/candidates")
    @Operation(summary = "Vehículos disponibles con capacidad para la solicitud (para asignar manualmente)")
    public ApiResponse<List<AssignmentCandidateView>> candidates(@PathParam("id") UUID requestId) {
        return ApiResponse.of(service.candidates(requestId).stream().map(AssignmentCandidateView::of).toList());
    }

    @POST
    @Path("/requests/{id}/assign")
    @Operation(summary = "RN10: asigna un transportista a una solicitud con SLA vencido; genera oferta y envío")
    public ApiResponse<ManualAssignmentResult> assign(@PathParam("id") UUID requestId, @Valid ManualAssignment req) {
        return ApiResponse.of(ManualAssignmentResult.of(service.assign(requestId, req.toCommand())));
    }
}
