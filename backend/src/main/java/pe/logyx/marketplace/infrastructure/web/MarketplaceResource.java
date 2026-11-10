package pe.logyx.marketplace.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.marketplace.application.*;
import pe.logyx.marketplace.infrastructure.web.MarketplaceDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class MarketplaceResource {

    @Inject PublishRequestUseCase publish;
    @Inject PlaceBidUseCase placeBid;
    @Inject AcceptBidUseCase acceptBid;
    @Inject CancelRequestUseCase cancelRequest;
    @Inject MarketplaceQueries queries;

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
    public ApiResponse<List<RequestView>> mine() {
        return ApiResponse.of(RequestView.of(queries.myRequests()));
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
    public ApiResponse<RequestView> cancel(@PathParam("id") UUID id) {
        return ApiResponse.of(RequestView.of(cancelRequest.cancel(id)));
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
    public ApiResponse<List<RequestView>> marketplace() {
        return ApiResponse.of(RequestView.of(queries.openMarketplace()));
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
