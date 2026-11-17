package pe.logyx.marketplace.application;

import pe.logyx.shared.tenancy.SystemTx;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.event.AuctionClosed;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Auction;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * RN04: cierre por tiempo. Lo invoca el scheduler sin usuario autenticado,
 * así que la transacción corre sin RLS (rol dueño): por definición cruza organizaciones.
 * RF18/F07: avisa a la PYME (y, si cerró sin ofertas, a los operadores: queda asignable por RN10).
 */
@ApplicationScoped
public class CloseExpiredAuctionsUseCase {

    @Inject SystemTx systemTx;

    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject ShipmentRequestRepository requests;
    @Inject Event<AuctionClosed> closed;

    // SystemTx: job sin usuario; RN04 cierra subastas de todas las organizaciones.
    public int closeExpired() {
        return systemTx.call(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            List<Auction> expired = auctions.listOpenClosingBefore(now);
            if (expired.isEmpty()) return 0;
            Map<java.util.UUID, Long> counts = bids.countByAuctions(expired.stream().map(Auction::id).toList());
            var byId = requests.listByIds(expired.stream().map(Auction::requestId).toList()).stream()
                    .collect(java.util.stream.Collectors.toMap(r -> r.id(), r -> r));
            for (Auction a : expired) {
                a.close(Auction.CloseReason.TIMEOUT, now);
                auctions.save(a);
                var r = byId.get(a.requestId());
                if (r != null && r.isPublished()) {
                    closed.fire(new AuctionClosed(r.id(), r.requesterOrgId(), r.routeLabel(),
                            counts.getOrDefault(a.id(), 0L)));
                }
            }
            Log.infof("Subastas cerradas por tiempo: %d", expired.size());
            return expired.size();
        });
    }
}
