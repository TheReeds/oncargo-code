package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.event.BidPlaced;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.AuctionClosingPolicy;
import pe.logyx.marketplace.domain.BidEligibilityPolicy;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.UUID;

/** El transportista oferta en una subasta abierta (RN02, RN03, RN04). */
@ApplicationScoped
public class PlaceBidUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject FleetService fleet;
    @Inject RequestSummaries summaries;
    @Inject TenantContext tenant;
    @Inject SystemTx system;
    @Inject Event<BidPlaced> bidPlaced;

    @Transactional
    public BidSummary placeBid(UUID requestId, PlaceBidCommand cmd) {
        ShipmentRequest r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        r.ensureOpenForBids();
        Auction a = auctions.findByRequestForUpdate(r.id()).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        if (!a.acceptsBids(now)) {
            throw BusinessException.conflict("LOGYX-AUC-002", "La subasta ya cerró");
        }
        UUID me = tenant.orgId();
        if (bids.existsForCarrier(a.id(), me)) {
            throw BusinessException.conflict("LOGYX-BID-003", "Ya enviaste una oferta para esta solicitud");
        }
        BidEligibilityPolicy.ensureAboveFloor(cmd.amount(), r.pricing().priceFloor());          // RN03
        Vehicle v = fleet.ownVehicle(cmd.vehicleId());                                           // RN02: propio
        BidEligibilityPolicy.ensureVehicleEligible(v.isAvailable(), v.maxWeightKg(), r.cargo().weightKg());

        Bid b = Bid.place(a, r, me, v, cmd.amount(), cmd.notes());
        bids.save(b);

        // RN04: cierre por número máximo de ofertas. Bajo RLS el transportista solo
        // ve su propia oferta, así que el conteo total se hace en contexto de sistema.
        // La tabla auctions no tiene datos privados: cerrarla bajo RLS está permitido.
        // La transacción de sistema no ve la oferta aún no confirmada: se cuentan
        // las ajenas (ya confirmadas, gracias al lock de la subasta) + la propia.
        long total = system.call(() -> bids.countByAuctionExcludingCarrier(a.id(), me)) + 1;
        boolean closed = AuctionClosingPolicy.reachedMaxBids(a, total);
        if (closed) {
            a.close(Auction.CloseReason.MAX_BIDS, now);
            auctions.save(a);
        }
        BidSummary summary = summaries.bids(java.util.List.of(b)).getFirst();
        // Notificación a la PYME (módulo notification) tras el commit
        bidPlaced.fire(new BidPlaced(b.id(), r.id(), r.requesterOrgId(), me, summary.carrierName(), b.amount(),
                r.routeLabel(), (int) total, closed));
        return summary;
    }
}
