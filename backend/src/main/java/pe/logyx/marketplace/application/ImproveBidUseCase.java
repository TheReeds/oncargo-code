package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetDocumentService;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationGuard;
import pe.logyx.marketplace.application.event.BidImproved;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.BidEligibilityPolicy;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ResourceScheduler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 2.8: con la subasta abierta el transportista mejora su oferta: <b>baja</b> el monto (nunca lo sube) y/o cambia el
 * vehículo por otro apto. Queda el historial ({@code bid_revisions}, solo suyo) y la PYME ve «mejorada». No es una oferta
 * nueva: no cuenta para el máximo de ofertas (RN04), conserva su antigüedad y no se puede usar para retirar y volver a
 * ofertar (BID-003 sigue valiendo). Con otro vehículo se repite RN02 por fecha, la agenda y los documentos de flota.
 */
@ApplicationScoped
public class ImproveBidUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject FleetService fleet;
    @Inject FleetDocumentService fleetDocs;
    @Inject OrganizationGuard guard;
    @Inject ResourceScheduler schedule;
    @Inject VehicleAgenda agenda;
    @Inject RequestSummaries summaries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<BidImproved> bidImproved;

    @Transactional
    public PlacedBid improve(UUID bidId, ImproveBidCommand cmd) {
        UUID me = tenant.orgId();
        guard.ensureCanTransact(me);                                                             // RN01
        Bid b = bids.findById(bidId).filter(x -> me.equals(x.carrierOrgId()))
                .orElseThrow(() -> BusinessException.notFound("Oferta"));
        ShipmentRequest r = requests.findById(b.requestId()).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (!r.isPublished()) {
            throw BusinessException.conflict("LOGYX-BID-005", "La solicitud ya no admite cambios en las ofertas");
        }
        Auction a = auctions.findByRequestForUpdate(r.id()).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        if (cmd.amount() != null) BidEligibilityPolicy.ensureAboveFloor(cmd.amount(), r.pricing().priceFloor()); // RN03
        Vehicle newVehicle = cmd.vehicleId() == null || cmd.vehicleId().equals(b.vehicleId()) ? null
                : fleet.ownVehicle(cmd.vehicleId());
        Bid.Revision revision = b.improve(a, cmd.amount(), newVehicle, now);
        List<BidWarning> warnings = List.of();
        if (newVehicle != null) {
            BidEligibilityPolicy.ensureVehicleEligibleOnDate(newVehicle, r.cargo());              // RN02
            schedule.ensureVehicleFree(newVehicle.id(), schedule.window(r.cargo().pickupDate(), r.pricing().distanceKm()),
                    b.loadGroupId(), null);                                                        // F11 / CAP-005
            VehicleAgenda.Assessment check = agenda.assess(newVehicle, r, b.loadGroupId());      // CAP-006/007, FLT-007/008
            VehicleAgenda.requireEligible(check);
            warnings = check.warnings();
            var seals = fleetDocs.sealsOf(newVehicle.id());
            b.stampSeals(seals.soatValidUntil(), seals.inspectionValidUntil());
        }
        bids.save(b);
        bids.recordRevision(b.id(), me, revision);
        audit.record(tenant.profileId(), me, "BID_IMPROVED", "bid", b.id(), "solicitud=" + r.id() + " monto="
                + revision.previousAmount() + "→" + revision.newAmount()
                + (newVehicle == null ? "" : " vehiculo=" + revision.previousPlate() + "→" + revision.newPlate()));
        BidSummary summary = summaries.bids(List.of(b)).getFirst();
        bidImproved.fire(new BidImproved(b.id(), r.id(), r.requesterOrgId(), me, summary.carrierName(), b.amount(),
                r.routeLabel(), newVehicle != null, b.improvedCount()));
        return new PlacedBid(summary, warnings);
    }

    /** Historial de mejoras de la oferta propia (404 si no es de mi organización). */
    @Transactional
    public List<BidRepository.RevisionRow> revisions(UUID bidId) {
        UUID me = tenant.orgId();
        bids.findById(bidId).filter(x -> me.equals(x.carrierOrgId())).orElseThrow(() -> BusinessException.notFound("Oferta"));
        return bids.listRevisions(bidId);
    }
}
