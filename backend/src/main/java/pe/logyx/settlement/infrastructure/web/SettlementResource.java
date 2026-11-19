package pe.logyx.settlement.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.settlement.application.SettlementService;
import pe.logyx.settlement.domain.model.SettlementStatus;
import pe.logyx.settlement.infrastructure.web.SettlementDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.security.OrgAdminOnly;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Cobros sin pasarela (roadmap 2.12, ADR-0014). El conductor no tiene acceso. */
@Path("/api/v1")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
public class SettlementResource {

    @Inject SettlementService service;
    @Inject TenantContext tenant;

    private SettlementView view(SettlementService.Detail d) {
        return SettlementView.of(d, tenant.orgType(), tenant.orgId(), tenant.isDriver());
    }

    @GET
    @Path("/settlements")
    @Operation(summary = "Cobros de envíos entregados: «Por pagar» (PYME), «Por cobrar» (transportista) o todos (operador)",
            description = "Bajo RLS: cada parte ve sus envíos. Orden: pendientes primero por vencimiento. La PYME no "
                    + "recibe la comisión. commissionPending y carrierOrgId son para el transportista y el operador.")
    public ApiResponse<List<SettlementRowView>> list(
            @Parameter(description = "Estados separados por coma (PENDING_INVOICE, INVOICED, PAID, CONFIRMED, DISPUTED).")
            @QueryParam("status") String status,
            @Parameter(description = "true = solo vencidos sin pagar.") @QueryParam("overdue") Boolean overdue,
            @Parameter(description = "Transportista (operador).") @QueryParam("carrierOrgId") UUID carrierOrgId,
            @Parameter(description = "true = solo con comisión por cobrar.") @QueryParam("commissionPending") Boolean commissionPending,
            @Parameter(description = "Entregados desde (yyyy-MM-dd, Lima).") @QueryParam("from")
            @Schema(type = SchemaType.STRING, format = "date") String from,
            @Parameter(description = "Entregados hasta (yyyy-MM-dd, Lima).") @QueryParam("to")
            @Schema(type = SchemaType.STRING, format = "date") String to,
            @QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        Set<SettlementStatus> statuses = EnumSet.noneOf(SettlementStatus.class);
        if (status != null && !status.isBlank()) {
            for (String s : status.split(",")) {
                try {
                    statuses.add(SettlementStatus.valueOf(s.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw BusinessException.badRequest("LOGYX-VAL-001", "Estado de cobro desconocido: " + s.trim(), "status");
                }
            }
        }
        OrgType viewer = tenant.orgType();
        return ApiResponse.of(service.list(statuses, overdue, carrierOrgId, commissionPending, date(from, "from"),
                date(to, "to"), PageRequest.of(page, size)).map(l -> SettlementRowView.of(l, viewer)));
    }

    @GET
    @Path("/settlements/summary")
    @Operation(summary = "Totales de cobros: por pagar/cobrar, vencidos, por confirmar, en disputa y comisión pendiente")
    public ApiResponse<SettlementSummaryView> summary() {
        return ApiResponse.of(SettlementSummaryView.of(service.summary()));
    }

    @GET
    @Path("/shipments/{id}/settlement")
    @Operation(summary = "Cobro de un envío entregado (estado, montos con IGV, comprobante, pago, disputa, historial)",
            description = "LOGYX-STL-001 (409) si el envío aún no se entrega. La PYME ve los datos de cobro del "
                    + "transportista y no ve la comisión.")
    public ApiResponse<SettlementView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(view(service.detail(id)));
    }

    @POST
    @Path("/shipments/{id}/settlement/invoice")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "El transportista registra su comprobante (factura o boleta) del flete",
            description = "Desde aquí el precio final queda fijo (los ajustes se bloquean: LOGYX-ADJ-009). STL-002 "
                    + "estado no válido, STL-003 ajustes pendientes, STL-005 (403) otra parte, STL-009 documento que no "
                    + "es CARRIER_INVOICE de este envío.")
    public ApiResponse<SettlementView> invoice(@PathParam("id") UUID id, @Valid @NotNull RegisterInvoice req) {
        return ApiResponse.of(view(service.invoice(id, req.invoiceNumber(), req.issuedOn(), req.documentId())));
    }

    @POST
    @Path("/shipments/{id}/settlement/payment")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "La PYME registra el pago hecho fuera de la plataforma (transferencia, Yape, Plin…)",
            description = "STL-002 ya pagado o en disputa, STL-003 ajustes pendientes, STL-009 documento que no es "
                    + "PAYMENT_PROOF de este envío. Número de operación obligatorio salvo efectivo.")
    public ApiResponse<SettlementView> pay(@PathParam("id") UUID id, @Valid @NotNull RegisterPayment req) {
        return ApiResponse.of(view(service.pay(id, req.method(), req.operationNumber(), req.paidOn(), req.amount(),
                req.documentId())));
    }

    @POST
    @Path("/shipments/{id}/settlement/confirm")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "El transportista confirma que recibió el pago", description = "STL-002 si no está PAID.")
    public ApiResponse<SettlementView> confirm(@PathParam("id") UUID id) {
        return ApiResponse.of(view(service.confirm(id)));
    }

    @POST
    @Path("/shipments/{id}/settlement/dispute")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @Operation(summary = "Una parte objeta el cobro (facturado o pagado, sin confirmar)",
            description = "STL-004 (409) estado no disputable. Lo resuelve el operador; quien la abrió puede retirarla.")
    public ApiResponse<SettlementView> dispute(@PathParam("id") UUID id, @Valid @NotNull OpenSettlementDispute req) {
        return ApiResponse.of(view(service.dispute(id, req.reason())));
    }

    @POST
    @Path("/shipments/{id}/settlement/resolve")
    @Operation(summary = "Cierra la disputa: el operador elige el estado; quien la abrió la retira",
            description = "STL-002 si no está en disputa, STL-005 (403) si no te toca. El operador debe dejar una nota.")
    public ApiResponse<SettlementView> resolve(@PathParam("id") UUID id, @Valid @NotNull ResolveSettlementDispute req) {
        return ApiResponse.of(view(service.resolve(id, req.status(), req.note())));
    }

    @GET
    @Path("/settlements/commission-collections")
    @RolesAllowed({Roles.CARRIER, Roles.OPERATOR})
    @Operation(summary = "Cobros de comisión registrados por OnCargo (transportista: los suyos; operador: todos o uno)")
    public ApiResponse<List<CommissionCollectionView>> collections(@QueryParam("carrierOrgId") UUID carrierOrgId,
                                                                   @QueryParam("page") Integer page,
                                                                   @QueryParam("size") Integer size) {
        return ApiResponse.of(service.commissionCollections(carrierOrgId, PageRequest.of(page, size))
                .map(CommissionCollectionView::of));
    }

    @POST
    @Path("/settlements/commission-collections")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "El operador registra que cobró la comisión (con su IGV) de envíos de un transportista",
            description = "El monto se calcula: suma de comisión + IGV de la comisión de cada envío sobre su precio final. "
                    + "STL-006 ya cobrada, sin comisión o en disputa; STL-010 envío de otro transportista o no entregado.")
    public ApiResponse<CommissionCollectionView> collect(@Valid @NotNull CollectCommission req) {
        return ApiResponse.of(CommissionCollectionView.of(service.collectCommission(req.carrierOrgId(), req.shipmentIds(),
                req.method(), req.operationNumber(), req.paidOn(), req.note())));
    }

    private static LocalDate date(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Fecha inválida (yyyy-MM-dd)", field);
        }
    }
}
