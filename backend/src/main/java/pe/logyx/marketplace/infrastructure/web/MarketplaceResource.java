package pe.logyx.marketplace.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.marketplace.application.*;
import pe.logyx.marketplace.infrastructure.web.MarketplaceDtos.*;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class MarketplaceResource {

    @Inject PublishRequestUseCase publish;
    @Inject PlaceBidUseCase placeBid;
    @Inject AcceptBidUseCase acceptBid;
    @Inject CancelRequestUseCase cancelRequest;
    @Inject MarketplaceQueries queries;
    @Inject AuctionRecoveryUseCase recovery;

    // ----- PYME
    @POST
    @Path("/shipment-requests")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    public ApiResponse<RequestView> publish(@Valid CreateRequest req) {
        return ApiResponse.of(RequestView.of(publish.publish(req.toCommand())));
    }

    @GET
    @Path("/shipment-requests/mine")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<List<RequestView>> mine(
            @Parameter(description = "Página 0-based. Si se omite page y size, se devuelven todas.")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20 cuando se pagina).")
            @QueryParam("size") Integer size) {
        if (page == null && size == null) return ApiResponse.of(RequestView.of(queries.myRequests()));
        return ApiResponse.of(queries.myRequests(PageRequest.of(page, size)).map(RequestView::of));
    }

    @GET
    @Path("/shipment-requests/{id}/bids")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<List<BidView>> bids(@PathParam("id") UUID id) {
        return ApiResponse.of(queries.bidsFor(id).stream().map(BidView::of).toList());
    }

    @POST
    @Path("/shipment-requests/{id}/cancel")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Cancela una solicitud publicada (motivo opcional); avisa a los transportistas que ofertaron")
    public ApiResponse<RequestView> cancel(@PathParam("id") UUID id, @Valid CancelRequestBody req) {
        return ApiResponse.of(RequestView.of(cancelRequest.cancel(id, req == null ? null : req.reason())));
    }

    @POST
    @Path("/shipment-requests/{id}/reopen")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "F07: reabre la subasta que cerró por tiempo (LOGYX-AUC-003); admite nueva fecha de recojo")
    public ApiResponse<RequestView> reopen(@PathParam("id") UUID id, @Valid RescheduleRequest req) {
        return ApiResponse.of(RequestView.of(recovery.reopen(id, req == null ? null : req.pickupDate())));
    }

    @POST
    @Path("/shipment-requests/{id}/republish")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    @Operation(summary = "F07/F06: publica de nuevo una solicitud cancelada (misma carga, precio y subasta nuevos)")
    public ApiResponse<RequestView> republish(@PathParam("id") UUID id, @Valid RescheduleRequest req) {
        return ApiResponse.of(RequestView.of(recovery.republish(id, req == null ? null : req.pickupDate())));
    }

    @POST
    @Path("/bids/{id}/withdraw")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "F08: el transportista retira su oferta pendiente mientras la subasta está abierta "
            + "(LOGYX-BID-005); no puede volver a ofertar en esa subasta")
    public ApiResponse<BidView> withdraw(@PathParam("id") UUID id) {
        return ApiResponse.of(BidView.of(placeBid.withdraw(id)));
    }

    @POST
    @Path("/bids/{id}/accept")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<AcceptResult> accept(@PathParam("id") UUID id) {
        return ApiResponse.of(AcceptResult.of(acceptBid.accept(id)));
    }

    // ----- Transportista
    @GET
    @Path("/shipment-requests")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<RequestView>> marketplace(
            @Parameter(description = "Código de ciudad de origen (p. ej. LIMA).")
            @QueryParam("originCity") String originCity,
            @Parameter(description = "Código de ciudad de destino (p. ej. JULIACA).")
            @QueryParam("destinationCity") String destinationCity,
            @Parameter(description = "Fecha de recojo mínima, ISO (yyyy-MM-dd), inclusive.")
            @QueryParam("pickupFrom") @Schema(type = SchemaType.STRING, format = "date") String pickupFrom,
            @Parameter(description = "Fecha de recojo máxima, ISO (yyyy-MM-dd), inclusive.")
            @QueryParam("pickupTo") @Schema(type = SchemaType.STRING, format = "date") String pickupTo,
            @Parameter(description = "Peso máximo de la carga en kg (inclusive).")
            @QueryParam("maxWeightKg") BigDecimal maxWeightKg,
            @Parameter(description = "Tipo de carga (GENERAL, FRAGILE, PERISHABLE, HAZARDOUS, BULK, OVERSIZED).")
            @QueryParam("cargoType") String cargoType,
            @Parameter(description = "Orden: pickup (por omisión), price_desc o recent.")
            @QueryParam("sort") String sort,
            @Parameter(description = "Página 0-based (por omisión 0).")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).")
            @QueryParam("size") Integer size) {
        var filter = MarketplaceFilter.of(originCity, destinationCity,
                MarketplaceFilter.date(pickupFrom, "pickupFrom"), MarketplaceFilter.date(pickupTo, "pickupTo"), maxWeightKg,
                cargoType, sort);
        return ApiResponse.of(queries.openMarketplace(filter, PageRequest.of(page, size)).map(RequestView::of));
    }

    @GET
    @Path("/shipment-requests/my-bids")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<RequestView>> myBids() {
        return ApiResponse.of(RequestView.of(queries.myBidRequests()));
    }

    @POST
    @Path("/shipment-requests/{id}/bids")
    @RolesAllowed(Roles.CARRIER)
    @ResponseStatus(201)
    public ApiResponse<BidView> bid(@PathParam("id") UUID id, @Valid PlaceBid req) {
        return ApiResponse.of(BidView.of(placeBid.placeBid(id, req.toCommand())));
    }

    // ----- Común
    @GET
    @Path("/shipment-requests/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    public ApiResponse<RequestView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(RequestView.of(queries.detail(id)));
    }
}
