package pe.logyx.operator.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.marketplace.application.OperatorRequestFilter;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shipment.application.OperatorShipmentFilter;
import pe.logyx.operator.application.AuditFilter;
import pe.logyx.operator.application.OperatorService;
import pe.logyx.operator.infrastructure.web.OperatorDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;
import jakarta.validation.constraints.NotNull;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;

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
    @Path("/requests")
    @Operation(summary = "Listado global paginado de solicitudes, más recientes primero")
    public ApiResponse<List<OperatorRequestRow>> requests(
            @Parameter(description = "Estado (PUBLISHED, MATCHED, CANCELLED).")
            @QueryParam("status") String status,
            @Parameter(description = "Código de ciudad de origen (p. ej. LIMA).")
            @QueryParam("originCity") String originCity,
            @Parameter(description = "Código de ciudad de destino (p. ej. JULIACA).")
            @QueryParam("destinationCity") String destinationCity,
            @Parameter(description = "Creada desde esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
            @Parameter(description = "Creada hasta esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate,
            @Parameter(description = "Página 0-based (por omisión 0).")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).")
            @QueryParam("size") Integer size) {
        var filter = OperatorRequestFilter.of(status, originCity, destinationCity, fromDate, toDate);
        return ApiResponse.of(service.requests(filter, PageRequest.of(page, size)).map(OperatorRequestRow::of));
    }

    @GET
    @Path("/shipments")
    @Operation(summary = "Listado global paginado de envíos, más recientes primero")
    public ApiResponse<List<OperatorShipmentRow>> shipments(
            @Parameter(description = "Estado (PENDING, IN_TRANSIT, DELIVERED, CANCELLED).")
            @QueryParam("status") String status,
            @Parameter(description = "Código de ciudad de origen (p. ej. LIMA).")
            @QueryParam("originCity") String originCity,
            @Parameter(description = "Código de ciudad de destino (p. ej. JULIACA).")
            @QueryParam("destinationCity") String destinationCity,
            @Parameter(description = "Creado desde esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
            @Parameter(description = "Creado hasta esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate,
            @Parameter(description = "Página 0-based (por omisión 0).")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).")
            @QueryParam("size") Integer size) {
        var filter = OperatorShipmentFilter.of(status, originCity, destinationCity, fromDate, toDate);
        return ApiResponse.of(service.shipments(filter, PageRequest.of(page, size)).map(OperatorShipmentRow::of));
    }

    @GET
    @Path("/requests/{id}/candidates")
    @Operation(summary = "Vehículos disponibles con capacidad para la solicitud (para asignar manualmente)")
    public ApiResponse<List<AssignmentCandidateView>> candidates(@PathParam("id") UUID requestId) {
        return ApiResponse.of(service.candidates(requestId).stream().map(AssignmentCandidateView::of).toList());
    }

    // ----- Auditoría (F05)
    @GET
    @Path("/audit")
    @Operation(summary = "Visor de auditoría: acciones registradas en la plataforma, paginadas, más recientes primero")
    public ApiResponse<List<AuditRow>> audit(
            @Parameter(description = "Id del perfil que ejecutó la acción.") @QueryParam("actorId") String actorId,
            @Parameter(description = "Id de la organización a la que pertenece el actor.")
            @QueryParam("organizationId") String organizationId,
            @Parameter(description = "Acción exacta (p. ej. REQUEST_PUBLISHED). Repetible o separada por comas: "
                    + "cualquiera de la lista.") @QueryParam("action") List<String> action,
            @Parameter(description = "Tipo de entidad (p. ej. shipment_request, bid, shipment, organization).")
            @QueryParam("entity") String entity,
            @Parameter(description = "Id de la entidad afectada.") @QueryParam("entityId") String entityId,
            @Parameter(description = "Desde esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("from") @Schema(type = SchemaType.STRING, format = "date") String from,
            @Parameter(description = "Hasta esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("to") @Schema(type = SchemaType.STRING, format = "date") String to,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        var filter = AuditFilter.of(actorId, organizationId, action, entity, entityId, from, to);
        return ApiResponse.of(service.audit(filter, PageRequest.of(page, size)).map(AuditRow::of));
    }

    @GET
    @Path("/audit/actions")
    @Operation(summary = "Acciones distintas presentes en la auditoría (para el filtro del visor), en orden alfabético")
    public ApiResponse<List<String>> auditActions() {
        return ApiResponse.of(service.auditActions());
    }

    // ----- Verificación de organizaciones (RN01, RF08, F05)
    @GET
    @Path("/organizations")
    @Operation(summary = "Bandeja de verificación: organizaciones por estado (PENDING por omisión), las más antiguas primero")
    public ApiResponse<List<OrganizationReviewRow>> organizations(
            @Parameter(description = "Estado de verificación (PENDING, VERIFIED, REJECTED, SUSPENDED); ALL = todos. "
                    + "Por omisión PENDING.") @QueryParam("status") String status,
            @Parameter(description = "Tipo de organización (PYME o CARRIER).") @QueryParam("type") String type,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        return ApiResponse.of(service.organizations(parseVerification(status), parseOrgType(type),
                PageRequest.of(page, size)).map(OrganizationReviewRow::of));
    }

    @POST
    @Path("/organizations/{id}/verify")
    @Operation(summary = "Verifica la organización: sello \"Verificado\" y habilita publicar/ofertar (RN01). "
            + "También reactiva una rechazada o suspendida")
    public ApiResponse<OrganizationReviewRow> verifyOrganization(@PathParam("id") UUID id, @Valid VerifyOrganization req) {
        return ApiResponse.of(OrganizationReviewRow.of(service.verifyOrganization(id, req == null ? null : req.note())));
    }

    @POST
    @Path("/organizations/{id}/reject")
    @Operation(summary = "Rechaza la verificación con motivo (LOGYX-ORG-006 sin motivo)")
    public ApiResponse<OrganizationReviewRow> rejectOrganization(@PathParam("id") UUID id,
                                                                 @Valid @NotNull OrganizationDecision req) {
        return ApiResponse.of(OrganizationReviewRow.of(service.rejectOrganization(id, req.reason())));
    }

    @POST
    @Path("/organizations/{id}/suspend")
    @Operation(summary = "Suspende la organización con motivo: no puede publicar, ofertar ni reservar (LOGYX-ORG-004)")
    public ApiResponse<OrganizationReviewRow> suspendOrganization(@PathParam("id") UUID id,
                                                                  @Valid @NotNull OrganizationDecision req) {
        return ApiResponse.of(OrganizationReviewRow.of(service.suspendOrganization(id, req.reason())));
    }

    private static Organization.VerificationStatus parseVerification(String raw) {
        if (raw == null || raw.isBlank()) return Organization.VerificationStatus.PENDING;
        if (raw.trim().equalsIgnoreCase("ALL")) return null;
        return java.util.Arrays.stream(Organization.VerificationStatus.values())
                .filter(v -> v.name().equalsIgnoreCase(raw.trim())).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Estado inválido: " + raw, "status"));
    }

    private static OrgType parseOrgType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return java.util.Arrays.stream(new OrgType[]{OrgType.PYME, OrgType.CARRIER})
                .filter(v -> v.name().equalsIgnoreCase(raw.trim())).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Tipo inválido: " + raw, "type"));
    }

    @POST
    @Path("/requests/{id}/assign")
    @Operation(summary = "RN10: asigna un transportista a una solicitud con SLA vencido; genera oferta y envío")
    public ApiResponse<ManualAssignmentResult> assign(@PathParam("id") UUID requestId, @Valid ManualAssignment req) {
        return ApiResponse.of(ManualAssignmentResult.of(service.assign(requestId, req.toCommand())));
    }
}
