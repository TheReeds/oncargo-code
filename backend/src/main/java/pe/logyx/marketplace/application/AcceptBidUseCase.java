package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.event.BidAccepted;
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
import pe.logyx.shipment.application.ResourceScheduler;
import pe.logyx.fleet.domain.VehicleCargoCompatibility;
import pe.logyx.identity.application.OrganizationGuard;
import pe.logyx.pricing.application.CommissionService;

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
    @Inject Event<BidAccepted> bidAccepted;
    @Inject OrganizationGuard guard;
    @Inject ResourceScheduler schedule;
    @Inject CommissionService commission;

    @Transactional
    public AcceptOutcome accept(UUID bidId) {
        guard.ensureCanTransact(tenant.orgId());                                                 // RN01 / suspensión
        Bid bid = bids.findForRequester(bidId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Oferta"));
        ShipmentRequest r = requests.findOwned(bid.requestId(), tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (!r.isPublished() || !bid.isPending()) {
            throw BusinessException.conflict("LOGYX-BID-004", "La solicitud ya no admite aceptar ofertas");
        }
        return award(r, bid, "BID_ACCEPTED");
    }

    /**
     * Adjudica la solicitud a {@code bid} (RN05): cierra la subasta, rechaza las demás ofertas y genera el envío.
     * Lo usan la aceptación de la PYME y la asignación manual del operador (RN10), cada una con su RLS.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public AcceptOutcome award(ShipmentRequest r, Bid bid, String auditAction) {
        var cargo = r.cargo();
        // RN02 también al aceptar (ofertas hechas antes de la matriz por tipo de carga, F09) y F11: el vehículo no
        // puede haber quedado comprometido en otro envío solapado (p. ej. ganó dos subastas a la vez).
        VehicleCargoCompatibility.ensureTypeAndVolume(bid.vehicleType(), null, cargo.cargoType(), null);
        schedule.ensureVehicleFree(bid.vehicleId(), schedule.window(cargo.pickupDate(), r.pricing().distanceKm()),
                bid.loadGroupId(), null);
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
                c.originCity(), c.originAddress(), c.destinationCity(), c.destinationAddress(), bid.loadGroupId(),
                commission.currentRate()));        // la tasa vigente queda guardada en el envío (ADR-0010)
        audit.record(tenant.profileId(), tenant.orgId(), auditAction, "bid", bid.id(),
                "monto=" + bid.amount() + " envio=" + shipmentId);
        bidAccepted.fire(new BidAccepted(bid.id(), r.id(), shipmentId, r.requesterOrgId(), bid.carrierOrgId(),
                bid.amount(), r.routeLabel(), bid.assignedByOperator(),
                others.stream().map(o -> new BidAccepted.RejectedBid(o.id(), o.carrierOrgId())).toList()));
        return new AcceptOutcome(shipmentId, summaries.bids(List.of(bid)).getFirst(),
                others.stream().map(Bid::id).toList());
    }
}
