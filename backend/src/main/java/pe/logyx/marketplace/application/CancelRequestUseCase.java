package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.event.RequestCancelled;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La PYME cancela una solicitud publicada (motivo opcional, RN07): cierra la subasta, rechaza las ofertas pendientes
 * y avisa a esos transportistas (F16).
 */
@ApplicationScoped
public class CancelRequestUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject RequestSummaries summaries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<RequestCancelled> cancelled;

    @Transactional
    public RequestSummary cancel(UUID requestId) {
        return cancel(requestId, null);
    }

    @Transactional
    public RequestSummary cancel(UUID requestId, String reason) {
        ShipmentRequest r = requests.findOwned(requestId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        r.cancel(reason);
        requests.save(r);
        Auction a = auctions.findByRequest(r.id()).orElseThrow();
        a.closeIfOpen(Auction.CloseReason.CANCELLED, OffsetDateTime.now());
        auctions.save(a);
        List<Bid> pending = bids.listPendingByRequest(r.id());
        pending.forEach(b -> { b.reject(); bids.save(b); });
        audit.record(tenant.profileId(), tenant.orgId(), "REQUEST_CANCELLED", "shipment_request", r.id(), r.cancelReason());
        if (!pending.isEmpty()) {
            cancelled.fire(new RequestCancelled(r.id(), r.requesterOrgId(), r.routeLabel(), r.cancelReason(),
                    pending.stream().map(Bid::carrierOrgId).distinct().toList()));
        }
        return summaries.of(r, false);
    }
}
