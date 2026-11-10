package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.CreateShipmentCommand;
import pe.logyx.shipment.application.CreateShipmentUseCase;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** RN05: la PYME acepta una oferta; las demás se rechazan y se genera el envío. */
@ApplicationScoped
public class AcceptBidUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject CreateShipmentUseCase shipments;
    @Inject RequestSummaries summaries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    @Transactional
    public AcceptOutcome accept(UUID bidId) {
        Bid bid = bids.findForRequester(bidId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Oferta"));
        ShipmentRequest r = requests.findOwned(bid.requestId(), tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (!r.isPublished() || !bid.isPending()) {
            throw BusinessException.conflict("LOGYX-BID-004", "La solicitud ya no admite aceptar ofertas");
        }
        Auction a = auctions.findByRequest(r.id()).orElseThrow();
        a.closeIfOpen(Auction.CloseReason.ACCEPTED, OffsetDateTime.now());
        auctions.save(a);

        bid.accept();
        bids.save(bid);
        List<Bid> others = bids.listPendingByRequest(r.id()).stream()
                .filter(o -> !o.id().equals(bid.id())).toList();
        others.forEach(o -> { o.reject(); bids.save(o); });

        r.matchTo(bid.carrierOrgId());
        requests.save(r);

        var c = r.cargo();
        UUID shipmentId = shipments.createFromAcceptedBid(new CreateShipmentCommand(
                r.id(), bid.id(), r.requesterOrgId(), bid.carrierOrgId(), bid.vehicleId(), bid.amount(),
                c.originCity(), c.originAddress(), c.destinationCity(), c.destinationAddress()));
        audit.record(tenant.profileId(), tenant.orgId(), "BID_ACCEPTED", "bid", bid.id(),
                "monto=" + bid.amount() + " envio=" + shipmentId);
        return new AcceptOutcome(shipmentId, summaries.bids(List.of(bid)).getFirst(),
                others.stream().map(Bid::id).toList());
    }
}
