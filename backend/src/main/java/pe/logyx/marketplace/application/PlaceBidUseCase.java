package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationGuard;
import pe.logyx.marketplace.application.event.BidPlaced;
import pe.logyx.marketplace.application.event.BidWithdrawn;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.AuctionClosingPolicy;
import pe.logyx.marketplace.domain.BidEligibilityPolicy;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ResourceScheduler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El transportista oferta en una subasta abierta (RN01 verificado, RN02 vehículo apto, RN03 piso, RN04 cierre) o
 * retira su oferta mientras la subasta sigue abierta (F08).
 */
@ApplicationScoped
public class PlaceBidUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject FleetService fleet;
    @Inject OrganizationGuard guard;
    @Inject ResourceScheduler schedule;
    @Inject RequestSummaries summaries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject SystemTx system;
    @Inject Event<BidPlaced> bidPlaced;
    @Inject Event<BidWithdrawn> bidWithdrawn;

    @Inject VehicleAgenda agenda;

    /** Oferta sin avisos de agenda (Smart Load, viajes): los avisos solo importan a quien ofertó a mano. */
    @Transactional
    public BidSummary placeBid(UUID requestId, PlaceBidCommand cmd) {
        return placeBidWithWarnings(requestId, cmd).summary();
    }

    /**
     * Oferta con RN01–RN04, RN02 por fecha de recojo y RN07. {@code warnings}: la agenda del vehículo ya tiene ofertas
     * pendientes solapadas (LOGYX-CAP-006); el tope es {@code logyx.bids.max-overlapping-per-vehicle} (CAP-007).
     */
    @Transactional
    public PlacedBid placeBidWithWarnings(UUID requestId, PlaceBidCommand cmd) {
        UUID me = tenant.orgId();
        guard.ensureCanTransact(me);                                                             // RN01
        ShipmentRequest r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        r.ensureOpenForBids();
        Auction a = auctions.findByRequestForUpdate(r.id()).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        if (!a.acceptsBids(now)) {
            throw BusinessException.conflict("LOGYX-AUC-002", "La subasta ya cerró");
        }
        bids.findForCarrier(a.id(), me).ifPresent(existing -> {
            throw BusinessException.conflict("LOGYX-BID-003", existing.status() == Bid.Status.WITHDRAWN
                    ? "Retiraste tu oferta en esta subasta: no puedes volver a ofertar"
                    : "Ya enviaste una oferta para esta solicitud");
        });
        BidEligibilityPolicy.ensureAboveFloor(cmd.amount(), r.pricing().priceFloor());          // RN03
        Vehicle v = fleet.ownVehicle(cmd.vehicleId());                                           // RN02: propio
        BidEligibilityPolicy.ensureVehicleEligibleOnDate(v, r.cargo());                          // RN02: mantenimiento, kg, tipo, m³
        var c = r.cargo();
        schedule.ensureVehicleFree(v.id(), schedule.window(c.pickupDate(), r.pricing().distanceKm()),
                cmd.loadGroupId(), null);                                                         // F11 / CAP-005 (con el bloqueo)
        VehicleAgenda.Assessment agendaCheck = agenda.assess(v, r, cmd.loadGroupId());           // CAP-007 y aviso CAP-006
        VehicleAgenda.requireEligible(agendaCheck);

        Bid b = Bid.place(a, r, me, v, cmd.amount(), cmd.notes(), cmd.loadGroupId());            // RN07 en notas
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
        BidSummary summary = summaries.bids(List.of(b)).getFirst();
        // Notificación a la PYME (módulo notification) tras el commit
        bidPlaced.fire(new BidPlaced(b.id(), r.id(), r.requesterOrgId(), me, summary.carrierName(), b.amount(),
                r.routeLabel(), (int) total, closed));
        return new PlacedBid(summary, agendaCheck.warnings());
    }

    /** F08: retira la oferta propia (pendiente) mientras la subasta acepta ofertas; no se puede volver a ofertar. */
    @Transactional
    public BidSummary withdraw(UUID bidId) {
        UUID me = tenant.orgId();
        Bid b = bids.findById(bidId).filter(x -> me.equals(x.carrierOrgId()))
                .orElseThrow(() -> BusinessException.notFound("Oferta"));
        ShipmentRequest r = requests.findById(b.requestId()).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        Auction a = auctions.findByRequestForUpdate(r.id()).orElseThrow();
        b.withdraw(a, OffsetDateTime.now());
        if (!r.isPublished()) {
            throw BusinessException.conflict("LOGYX-BID-005", "La solicitud ya no admite cambios en las ofertas");
        }
        bids.save(b);
        long total = system.call(() -> bids.countByAuctionExcludingCarrier(a.id(), me));
        audit.record(tenant.profileId(), me, "BID_WITHDRAWN", "bid", b.id(), "solicitud=" + r.id());
        BidSummary summary = summaries.bids(List.of(b)).getFirst();
        bidWithdrawn.fire(new BidWithdrawn(b.id(), r.id(), r.requesterOrgId(), me, summary.carrierName(),
                r.routeLabel(), (int) total));
        return summary;
    }
}
