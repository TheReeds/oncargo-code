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
import pe.logyx.shared.config.domain.ParameterKey;
import pe.logyx.shared.domain.Slice;
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
            @Parameter(description = "Búsqueda por texto (2-60 caracteres): código OC-…, referencia de la PYME, razón "
                    + "social o RUC del solicitante o del transportista adjudicado, placa, descripción de la carga o id.")
            @QueryParam("q") String q,
            @Parameter(description = "Página 0-based (por omisión 0).")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).")
            @QueryParam("size") Integer size) {
        var filter = OperatorRequestFilter.of(status, originCity, destinationCity, fromDate, toDate);
        return ApiResponse.of(service.requests(filter, q, PageRequest.of(page, size)).map(OperatorRequestRow::of));
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
            @Parameter(description = "Búsqueda por texto (2-60 caracteres): código y referencia de la solicitud, razón "
                    + "social o RUC de la PYME o del transportista, placa, conductor o id del envío.")
            @QueryParam("q") String q,
            @Parameter(description = "Página 0-based (por omisión 0).")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).")
            @QueryParam("size") Integer size) {
        var filter = OperatorShipmentFilter.of(status, originCity, destinationCity, fromDate, toDate);
        return ApiResponse.of(service.shipments(filter, q, PageRequest.of(page, size)).map(OperatorShipmentRow::of));
    }

    // ----- Ofertas y chat de una solicitud (solo lectura, ítem 1.13)
    @GET
    @Path("/requests/{id}/bids")
    @Operation(summary = "Ofertas de una solicitud, de menor a mayor monto (solo lectura, para mediar una disputa)")
    public ApiResponse<List<OperatorBidRow>> bids(@PathParam("id") UUID requestId) {
        return ApiResponse.of(service.bids(requestId).stream().map(OperatorBidRow::of).toList());
    }

    @GET
    @Path("/requests/{id}/threads")
    @Operation(summary = "Hilos de chat de una solicitud (solo lectura). Queda auditado: OPERATOR_VIEWED_CHAT",
            description = "El equipo de OnCargo puede revisar conversaciones para resolver disputas (política de "
                    + "privacidad). Cada consulta se registra en la auditoría con el motivo, si se indica.")
    public ApiResponse<List<OperatorThreadRow>> threads(
            @PathParam("id") UUID requestId,
            @Parameter(description = "Motivo de la consulta (opcional, hasta 200 caracteres); queda en la auditoría.")
            @QueryParam("reason") String reason) {
        return ApiResponse.of(service.threads(requestId, reason(reason)).stream().map(OperatorThreadRow::of).toList());
    }

    @GET
    @Path("/requests/{id}/threads/{carrierOrgId}/messages")
    @Operation(summary = "Mensajes de un hilo por cursor, en orden cronológico (solo lectura). Auditado al abrir el hilo",
            description = "meta.nextCursor = valor para 'before'. Solo la primera página (sin 'before') se registra en "
                    + "la auditoría como OPERATOR_VIEWED_CHAT.")
    public ApiResponse<List<OperatorMessageRow>> messages(
            @PathParam("id") UUID requestId, @PathParam("carrierOrgId") UUID carrierOrgId,
            @Parameter(description = "Id de mensaje: devuelve los anteriores a él (omitir = los más recientes).")
            @QueryParam("before") UUID before,
            @Parameter(description = "Máximo de mensajes (1-100, por omisión 30).") @QueryParam("size") Integer size,
            @Parameter(description = "Motivo de la consulta (opcional, hasta 200 caracteres); queda en la auditoría.")
            @QueryParam("reason") String reason) {
        Slice<pe.logyx.negotiation.application.MessageSummary> s =
                service.messages(requestId, carrierOrgId, before, size, reason(reason));
        return ApiResponse.of(s.map(OperatorMessageRow::of));
    }

    private static String reason(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.trim().length() > 200) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "El motivo admite hasta 200 caracteres", "reason");
        }
        return raw.trim();
    }

    // ----- Parámetros de sistema (V18)
    @GET
    @Path("/parameters")
    @Operation(summary = "Parámetros de sistema vigentes (comisión, SLA de asignación, umbrales de incidencias)",
            description = "version 0 = rige el valor por omisión de la configuración; cada cambio sube la versión.")
    public ApiResponse<List<ParameterRow>> parameters() {
        return ApiResponse.of(service.parameters().stream().map(ParameterRow::of).toList());
    }

    @PUT
    @Path("/parameters/{key}")
    @Operation(summary = "Cambia un parámetro de sistema con motivo (queda en el historial y en la auditoría)",
            description = "LOGYX-PAR-001 valor inválido o fuera de rango (value), LOGYX-PAR-002 falta el motivo, "
                    + "LOGYX-PAR-003 aviso ≥ incumplimiento, LOGYX-PAR-004 otro operador lo cambió (expectedVersion). "
                    + "404 si la clave no existe.")
    public ApiResponse<ParameterRow> updateParameter(@PathParam("key") String key, @Valid @NotNull UpdateParameter req) {
        return ApiResponse.of(ParameterRow.of(
                service.updateParameter(ParameterKey.require(key), req.value(), req.reason(), req.expectedVersion())));
    }

    @GET
    @Path("/parameters/history")
    @Operation(summary = "Historial de cambios de parámetros, el más reciente primero")
    public ApiResponse<List<ParameterChangeRow>> parameterHistory(
            @Parameter(description = "Clave del parámetro (commission_rate, assignment_sla_hours, "
                    + "incident_warn_hours, incident_breach_hours). Sin ella, todos.") @QueryParam("key") String key,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        ParameterKey k = key == null || key.isBlank() ? null : ParameterKey.require(key);
        return ApiResponse.of(service.parameterHistory(k, PageRequest.of(page, size)).map(ParameterChangeRow::of));
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
            @Parameter(description = "Búsqueda por texto (2-60 caracteres): razón social, RUC, ciudad, correo de un "
                    + "usuario, placa de un vehículo o id.") @QueryParam("q") String q,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        return ApiResponse.of(service.organizations(parseVerification(status), parseOrgType(type), q,
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

    // ----- Ola 2 zona X (2.6, 2.7, 2.15)
    @jakarta.inject.Inject pe.logyx.operator.application.Organization360Service ops360;

    @GET
    @Path("/organizations/{id}/overview")
    @Operation(summary = "Ficha 360 de una organización: usuarios, flota, envíos, incidencias, reputación, tasas, "
            + "verificación, cambios legales, auditoría y señales (2.15)")
    public ApiResponse<Operator360Dtos.Organization360View> overview(@PathParam("id") UUID id) {
        var o = ops360.overview(id);
        var ids = new java.util.HashSet<UUID>();
        o.signals().forEach(sg -> ids.addAll(sg.organizationIds()));
        return ApiResponse.of(Operator360Dtos.Organization360View.of(o, ops360.organizationsOf(ids)));
    }

    @GET
    @Path("/signals")
    @Operation(summary = "Señales de fraude: teléfono, placa, licencia o IP de registro repetidos entre organizaciones, "
            + "tasas de retiro o cancelación altas y cuentas nuevas con muchas ofertas bajo el sugerido (2.15)")
    public ApiResponse<List<Operator360Dtos.FraudSignalView>> signals() {
        var list = ops360.signals();
        var ids = new java.util.HashSet<UUID>();
        list.forEach(sg -> ids.addAll(sg.organizationIds()));
        var names = ops360.organizationsOf(ids);
        return ApiResponse.of(list.stream().map(sg -> Operator360Dtos.FraudSignalView.of(sg, names)).toList());
    }

    @POST
    @Path("/users/{id}/suspend")
    @Operation(summary = "Suspende a una persona de una PYME o transportista con motivo: cierra sus sesiones y su ADMIN no "
            + "la reactiva (LOGYX-USR-007). Las cuentas del operador no (LOGYX-USR-008)")
    public ApiResponse<Operator360Dtos.OrgMemberView> suspendUser(@PathParam("id") UUID id,
                                                                  @Valid @NotNull Operator360Dtos.AccountActionReason req) {
        return ApiResponse.of(Operator360Dtos.OrgMemberView.of(ops360.suspendUser(id, req.reason())));
    }

    @POST
    @Path("/users/{id}/reactivate")
    @Operation(summary = "Levanta la suspensión del operador y reactiva a la persona")
    public ApiResponse<Operator360Dtos.OrgMemberView> reactivateUser(@PathParam("id") UUID id,
                                                                     @Valid Operator360Dtos.AccountReactivation req) {
        return ApiResponse.of(Operator360Dtos.OrgMemberView.of(ops360.reactivateUser(id, req == null ? null : req.reason())));
    }

    @POST
    @Path("/organizations/{id}/legal-change")
    @jakarta.ws.rs.Consumes(jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Cambio asistido de razón social o RUC con documento (PDF/JPG/PNG) y motivo (2.15)",
            description = "Mismo RUC: corrige la razón social. RUC nuevo: crea una organización sucesora VERIFIED que hereda "
                    + "la reputación, las personas y la flota; la anterior queda SUSPENDED (LOGYX-ORG-008 si tiene envíos, "
                    + "solicitudes, ofertas o viajes vivos; LOGYX-ORG-002 RUC ya registrado; LOGYX-ORG-009 sin cambios).")
    public ApiResponse<Operator360Dtos.LegalChangeResult> legalChange(
            @PathParam("id") UUID id,
            @org.jboss.resteasy.reactive.RestForm("legalName") String legalName,
            @org.jboss.resteasy.reactive.RestForm("ruc") String ruc,
            @org.jboss.resteasy.reactive.RestForm("reason") String reason,
            @org.jboss.resteasy.reactive.RestForm("file") @Schema(type = SchemaType.STRING, format = "binary")
            org.jboss.resteasy.reactive.multipart.FileUpload file) {
        if (file == null || file.uploadedFile() == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Adjunta el documento de sustento", "file");
        }
        var out = ops360.legalChange(id, legalName, ruc, reason, file.fileName(), file.size(),
                () -> java.nio.file.Files.newInputStream(file.uploadedFile()));
        return ApiResponse.of(new Operator360Dtos.LegalChangeResult(out.organization().id(), out.successorCreated(),
                Operator360Dtos.LegalChangeView.of(out.change())));
    }

    @GET
    @Path("/legal-changes/{id}/file")
    @jakarta.ws.rs.Produces({"image/jpeg", "image/png", "application/pdf"})
    @Operation(summary = "Descarga el documento de sustento de un cambio de razón social o RUC")
    @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "200",
            description = "Contenido del archivo", content = @org.eclipse.microprofile.openapi.annotations.media.Content(
            schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public jakarta.ws.rs.core.Response legalChangeFile(@PathParam("id") UUID id) {
        var f = ops360.legalChangeFile(id);
        return jakarta.ws.rs.core.Response.ok(f.content(), f.change().documentContentType())
                .header("Content-Disposition", "inline; filename=\"" + f.change().documentName()
                        .replaceAll("[^\\x20-\\x7E]", "").replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store").build();
    }

    @GET
    @Path("/adjustments")
    @Operation(summary = "Ajustes escalados: la PYME no respondió a tiempo y los decide el operador (2.6)")
    public ApiResponse<List<Operator360Dtos.EscalatedAdjustmentRow>> escalatedAdjustments() {
        return ApiResponse.of(ops360.escalatedAdjustments().stream().map(Operator360Dtos.EscalatedAdjustmentRow::of)
                .toList());
    }

    @GET
    @Path("/incidents")
    @Operation(summary = "Bandeja de incidencias del operador con SLA por severidad (2.7)")
    public ApiResponse<List<Operator360Dtos.OperatorIncidentRow>> incidents(
            @Parameter(description = "Estados separados por coma; por omisión OPEN,IN_REVIEW.") @QueryParam("status") String status,
            @Parameter(description = "Severidades separadas por coma (LOW, MEDIUM, HIGH, CRITICAL).")
            @QueryParam("severity") String severity,
            @Parameter(description = "me = mías; none = sin responsable.") @QueryParam("assignee") String assignee,
            @Parameter(description = "true = solo con el SLA vencido.") @QueryParam("breached") Boolean breached,
            @Parameter(description = "Organización (PYME o transportista) parte del envío.")
            @QueryParam("organizationId") UUID organizationId,
            @Parameter(description = "due = las que vencen antes primero (por omisión); recent = recientes primero.")
            @QueryParam("order") String order,
            @QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        var statuses = parseEnums(status, pe.logyx.incident.domain.model.IncidentStatus.class, "status");
        if (statuses.isEmpty()) statuses = List.of(pe.logyx.incident.domain.model.IncidentStatus.OPEN,
                pe.logyx.incident.domain.model.IncidentStatus.IN_REVIEW);
        var severities = parseEnums(severity, pe.logyx.incident.domain.model.IncidentSeverity.class, "severity");
        UUID mine = "me".equalsIgnoreCase(assignee) ? tenant.profileId() : null;
        boolean none = "none".equalsIgnoreCase(assignee);
        var filter = new pe.logyx.incident.application.OperatorIncidentFilter(statuses, severities, mine, none,
                Boolean.TRUE.equals(breached) ? java.time.OffsetDateTime.now() : null, organizationId,
                !"recent".equalsIgnoreCase(order));
        return ApiResponse.of(ops360.incidentInbox(filter, pe.logyx.shared.domain.PageRequest.of(page, size))
                .map(Operator360Dtos.OperatorIncidentRow::of));
    }

    @jakarta.inject.Inject pe.logyx.shared.tenancy.TenantContext tenant;

    private static <E extends Enum<E>> List<E> parseEnums(String raw, Class<E> type, String field) {
        if (raw == null || raw.isBlank()) return List.of();
        List<E> out = new java.util.ArrayList<>();
        for (String p : raw.split(",")) {
            String v = p.trim();
            if (v.isEmpty()) continue;
            out.add(java.util.Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equalsIgnoreCase(v)).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Valor inválido: " + v, field)));
        }
        return out;
    }
}
