package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.UUID;

/** La PYME cancela una solicitud publicada: cierra la subasta y rechaza las ofertas pendientes. */
@ApplicationScoped
public class CancelRequestUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject RequestSummaries summaries;
    @Inject TenantContext tenant;

    @Transactional
    public RequestSummary cancel(UUID requestId) {
        ShipmentRequest r = requests.findOwned(requestId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        r.cancel();
        requests.save(r);
        Auction a = auctions.findByRequest(r.id()).orElseThrow();
        a.closeIfOpen(Auction.CloseReason.CANCELLED, OffsetDateTime.now());
        auctions.save(a);
        bids.listPendingByRequest(r.id()).forEach(b -> { b.reject(); bids.save(b); });
        return summaries.of(r, false);
    }
}
