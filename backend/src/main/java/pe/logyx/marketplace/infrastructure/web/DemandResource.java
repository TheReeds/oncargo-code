package pe.logyx.marketplace.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.marketplace.application.*;
import pe.logyx.marketplace.domain.model.PreferenceKind;
import pe.logyx.marketplace.infrastructure.web.DemandDtos.*;
import pe.logyx.marketplace.infrastructure.web.MarketplaceDtos.AcceptResult;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.idempotency.IdempotencyKeys;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lado PYME de la Ola 2 (zona P): transportistas preferidos y bloqueados (2.9), segunda oferta (2.9), bandeja de
 * ofertas y acciones masivas (2.10). Comparte la raíz {@code /api/v1} con el resto del marketplace.
 */
@Path("/api/v1")
public class DemandResource {

    @Inject CarrierPreferenceService preferences;
    @Inject InviteCarriersUseCase invitations;
    @Inject BackupBidService backups;
    @Inject BatchRequestsUseCase batch;
    @Inject OffersInboxService inbox;

    // ----- Preferidos y bloqueados

    @GET
    @Path("/carrier-preferences")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Transportistas que la PYME marcó como preferidos o bloqueados (la marca es privada de la PYME)")
    public ApiResponse<List<CarrierPreferenceView>> listPreferences(
            @Parameter(description = "PREFERRED o BLOCKED; sin valor trae ambas.") @QueryParam("kind") PreferenceKind kind) {
        return ApiResponse.of(preferences.list(kind).stream().map(CarrierPreferenceView::of).toList());
    }

    @PUT
    @Path("/carrier-preferences/{carrierOrgId}")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Marca o cambia la marca de un transportista. Bloqueado: no ve tus solicitudes ni puede ofertar; "
            + "preferido: se destaca y se le invita a ofertar. Idempotente")
    public ApiResponse<CarrierPreferenceView> mark(@PathParam("carrierOrgId") UUID carrierOrgId, @Valid SetPreference req) {
        return ApiResponse.of(CarrierPreferenceView.of(preferences.mark(carrierOrgId, req.kind())));
    }

    @DELETE
    @Path("/carrier-preferences/{carrierOrgId}")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Quita la marca de un transportista. Idempotente")
    public ApiResponse<Unmarked> unmark(@PathParam("carrierOrgId") UUID carrierOrgId) {
        preferences.unmark(carrierOrgId);
        return ApiResponse.of(new Unmarked(carrierOrgId));
    }

    @POST
    @Path("/shipment-requests/{id}/invite-preferred")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Invita a ofertar a tus transportistas preferidos (aviso a cada uno). Solo solicitudes publicadas")
    public ApiResponse<InviteResult> invite(@PathParam("id") UUID id) {
        return ApiResponse.of(new InviteResult(invitations.invitePreferred(id)));
    }

    // ----- Bandeja de ofertas

    @GET
    @Path("/offers/inbox")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Bandeja de ofertas: una fila por solicitud publicada con ofertas pendientes (mejor monto, "
            + "mejor Trust Score y cierre de la subasta). No incluye ofertas de transportistas bloqueados")
    public ApiResponse<OffersInboxView> offersInbox() {
        List<OffersInboxService.Row> rows = inbox.inbox();
        Set<UUID> preferred = preferences.ids(PreferenceKind.PREFERRED);
        List<OfferInboxRow> out = rows.stream().map(r -> OfferInboxRow.of(r, preferred)).toList();
        return ApiResponse.of(new OffersInboxView(out.size(), out.stream().mapToInt(OfferInboxRow::bidCount).sum(), out));
    }

    // ----- Acciones masivas (por ítem, sin atomicidad)

    @POST
    @Path("/shipment-requests/batch/cancel")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Cancela varias solicitudes publicadas con un motivo común. Resultado por solicitud (éxito o "
            + "código de error); NO es atómico: las que se pueden se cancelan aunque otras fallen")
    public ApiResponse<BatchResultView> batchCancel(@Valid BatchCancel req) {
        return ApiResponse.of(BatchResultView.of(batch.cancel(req.ids(), req.reason())));
    }

    @POST
    @Path("/shipment-requests/batch/reschedule")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Cambia la fecha de recojo de varias solicitudes publicadas sin ofertas pendientes (LOGYX-REQ-009 "
            + "si ya tienen). Resultado por solicitud; no es atómico")
    public ApiResponse<BatchResultView> batchReschedule(@Valid BatchReschedule req) {
        return ApiResponse.of(BatchResultView.of(batch.reschedule(req.ids(), req.pickupDate())));
    }

    @POST
    @Path("/shipment-requests/batch/duplicate")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Publica una copia de cada solicitud con el recojo el mismo día de la semana siguiente (RN01, RN03 "
            + "y RN07 por ítem). Con Idempotency-Key, reintentar el lote no duplica las copias ya creadas. No es atómico")
    public ApiResponse<BatchResultView> batchDuplicate(
            @Parameter(description = "Clave de idempotencia del lote (8-80 caracteres); cada copia deriva la suya.")
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid BatchDuplicate req) {
        String key = IdempotencyKeys.validate(idempotencyKey);
        if (key != null && key.length() > 43) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "Para un lote, Idempotency-Key admite hasta 43 caracteres", "Idempotency-Key");
        }
        return ApiResponse.of(BatchResultView.of(batch.duplicate(req.ids(), key)));
    }

    // ----- Segunda oferta: PYME

    @GET
    @Path("/shipment-requests/{id}/backup-bids")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Ofertas de respaldo de la solicitud y si se puede adjudicar a otra: el envío adjudicado debe "
            + "haberse cancelado en las 24 h siguientes a la adjudicación y el recojo no haber pasado")
    public ApiResponse<BackupOptionsView> backupOptions(@PathParam("id") UUID id) {
        return ApiResponse.of(BackupOptionsView.of(backups.options(id)));
    }

    @POST
    @Path("/bids/{id}/backup/request")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Pide al transportista de una oferta de respaldo que confirme que lleva la carga (aviso). "
            + "LOGYX-BCK-001/002/003 si no corresponde")
    public ApiResponse<BackupItemView> requestBackup(@PathParam("id") UUID bidId) {
        return ApiResponse.of(BackupItemView.of(backups.requestConfirmation(bidId)));
    }

    @POST
    @Path("/bids/{id}/backup/award")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Adjudica la solicitud a la oferta de respaldo que el transportista ya confirmó (nuevo envío con "
            + "la comisión vigente). LOGYX-BCK-004 si aún no confirmó")
    public ApiResponse<AcceptResult> awardBackup(@PathParam("id") UUID bidId) {
        return ApiResponse.of(AcceptResult.of(backups.award(bidId)));
    }

    // ----- Segunda oferta: transportista

    @GET
    @Path("/bids/backup-requests")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "Pedidos de confirmación de PYMEs que quieren adjudicarte su carga como segunda oferta")
    public ApiResponse<List<BackupRequestView>> backupRequests() {
        return ApiResponse.of(backups.mine().stream().map(BackupRequestView::of).toList());
    }

    @POST
    @Path("/bids/{id}/backup/confirm")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "Confirma que puedes llevar la carga si la PYME te la adjudica")
    public ApiResponse<BackupRequestView> confirmBackup(@PathParam("id") UUID bidId) {
        return ApiResponse.of(BackupRequestView.of(backups.respond(bidId, true)));
    }

    @POST
    @Path("/bids/{id}/backup/decline")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "Declina llevar la carga")
    public ApiResponse<BackupRequestView> declineBackup(@PathParam("id") UUID bidId) {
        return ApiResponse.of(BackupRequestView.of(backups.respond(bidId, false)));
    }
}
