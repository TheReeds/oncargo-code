package pe.logyx.negotiation.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.negotiation.application.NegotiationQueries;
import pe.logyx.negotiation.application.NegotiationService;
import pe.logyx.negotiation.infrastructure.web.NegotiationDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class NegotiationResource {

    @Inject NegotiationService service;
    @Inject NegotiationQueries queries;

    // ----- Chat (RF25, RN07)
    @GET
    @Path("/shipment-requests/{id}/threads")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @Operation(summary = "Hilos de chat de la solicitud (PYME: uno por transportista; transportista: el suyo)")
    public ApiResponse<List<ChatThreadView>> threads(@PathParam("id") UUID requestId) {
        return ApiResponse.of(queries.threads(requestId).stream().map(ChatThreadView::of).toList());
    }

    @GET
    @Path("/shipment-requests/{id}/threads/{carrierOrgId}/messages")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    @Operation(summary = "Historial del hilo por cursor, en orden cronológico; meta.nextCursor = valor para 'before'")
    public ApiResponse<List<MessageView>> messages(
            @PathParam("id") UUID requestId, @PathParam("carrierOrgId") UUID carrierOrgId,
            @Parameter(description = "Id de mensaje: devuelve los anteriores a él (omitir = los más recientes).")
            @QueryParam("before") UUID before,
            @Parameter(description = "Máximo de mensajes (1-100, por omisión 30).") @QueryParam("size") Integer size) {
        return ApiResponse.of(queries.history(requestId, carrierOrgId, before, size).map(MessageView::of));
    }

    @POST
    @Path("/shipment-requests/{id}/threads/{carrierOrgId}/messages")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @ResponseStatus(201)
    @Operation(summary = "Envía un mensaje; bloquea teléfonos, emails, URLs y usuarios de redes (RN07, LOGYX-NEG-001)")
    public ApiResponse<MessageView> post(@PathParam("id") UUID requestId,
                                         @PathParam("carrierOrgId") UUID carrierOrgId, @Valid PostMessage req) {
        return ApiResponse.of(MessageView.of(service.post(requestId, carrierOrgId, req.body())));
    }

    // ----- Contra-ofertas (RF27, RF28)
    @GET
    @Path("/bids/{id}/counter-offers")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    public ApiResponse<List<CounterOfferView>> counterOffers(@PathParam("id") UUID bidId) {
        return ApiResponse.of(queries.counterOffers(bidId).stream().map(CounterOfferView::of).toList());
    }

    @POST
    @Path("/bids/{id}/counter-offers")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    @Operation(summary = "La PYME propone un nuevo monto (≥ piso RN03; una contra-oferta pendiente por oferta)")
    public ApiResponse<CounterOfferView> propose(@PathParam("id") UUID bidId, @Valid ProposeCounterOffer req) {
        return ApiResponse.of(CounterOfferView.of(service.propose(bidId, req.amount(), req.message())));
    }

    @POST
    @Path("/counter-offers/{id}/accept")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "El transportista acepta: el monto de su oferta pasa a ser el de la contra-oferta")
    public ApiResponse<CounterOfferView> accept(@PathParam("id") UUID id) {
        return ApiResponse.of(CounterOfferView.of(service.accept(id)));
    }

    @POST
    @Path("/counter-offers/{id}/reject")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<CounterOfferView> reject(@PathParam("id") UUID id) {
        return ApiResponse.of(CounterOfferView.of(service.reject(id)));
    }
}
