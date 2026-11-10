package pe.logyx.marketplace;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.marketplace.MarketplaceDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class MarketplaceResource {

    @Inject MarketplaceService marketplace;

    // ----- PYME
    @POST
    @Path("/shipment-requests")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    public ApiResponse<RequestView> publish(@Valid CreateRequest req) {
        return ApiResponse.of(marketplace.publish(req));
    }

    @GET
    @Path("/shipment-requests/mine")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<List<RequestView>> mine() {
        return ApiResponse.of(marketplace.myRequests());
    }

    @GET
    @Path("/shipment-requests/{id}/bids")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<List<BidView>> bids(@PathParam("id") UUID id) {
        return ApiResponse.of(marketplace.bidsFor(id));
    }

    @POST
    @Path("/shipment-requests/{id}/cancel")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<RequestView> cancel(@PathParam("id") UUID id) {
        return ApiResponse.of(marketplace.cancel(id));
    }

    @POST
    @Path("/bids/{id}/accept")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<AcceptResult> accept(@PathParam("id") UUID id) {
        return ApiResponse.of(marketplace.accept(id));
    }

    // ----- Transportista
    @GET
    @Path("/shipment-requests")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<RequestView>> marketplace() {
        return ApiResponse.of(marketplace.openMarketplace());
    }

    @GET
    @Path("/shipment-requests/my-bids")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<RequestView>> myBids() {
        return ApiResponse.of(marketplace.myBidRequests());
    }

    @POST
    @Path("/shipment-requests/{id}/bids")
    @RolesAllowed(Roles.CARRIER)
    @ResponseStatus(201)
    public ApiResponse<BidView> bid(@PathParam("id") UUID id, @Valid PlaceBid req) {
        return ApiResponse.of(marketplace.placeBid(id, req));
    }

    // ----- Común
    @GET
    @Path("/shipment-requests/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    public ApiResponse<RequestView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(marketplace.detail(id));
    }
}
