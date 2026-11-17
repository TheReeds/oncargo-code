package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.application.OrganizationGuard;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.AuctionClosingPolicy;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.pricing.application.PricingService;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.time.OffsetDateTime;

/** La PYME publica una solicitud: se cotiza (precio sugerido + piso RN03) y la subasta abre en el acto. */
@ApplicationScoped
public class PublishRequestUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject OrganizationGuard guard;
    @Inject PricingService pricing;
    @Inject RequestSummaries summaries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    @ConfigProperty(name = "logyx.auction.duration") Duration auctionDuration;
    @ConfigProperty(name = "logyx.auction.max-bids") int maxBids;

    @Transactional
    public RequestSummary publish(PublishRequestCommand cmd) {
        return publish(cmd, null);
    }

    /** {@code republishedFrom}: solicitud cancelada de la que sale esta (F07), o null. */
    @Transactional
    public RequestSummary publish(PublishRequestCommand cmd, java.util.UUID republishedFrom) {
        Organization org = guard.ensureCanTransact(tenant.orgId());                        // RN01 (verificada)
        City origin = City.require(cmd.originCity());
        City destination = City.require(cmd.destinationCity());
        PriceQuote quote = pricing.quote(origin, destination, cmd.weightKg(), cmd.cargoType());

        ShipmentRequest r = ShipmentRequest.publish(org.id(), tenant.profileId(),
                new ShipmentRequest.Cargo(origin, cmd.originAddress(), destination, cmd.destinationAddress(),
                        cmd.cargoType(), cmd.cargoDescription(), cmd.weightKg(), cmd.volumeM3(), cmd.pickupDate()),
                new ShipmentRequest.Pricing(quote.distanceKm(), quote.suggestedPrice(), quote.priceFloor()),
                republishedFrom);
        requests.save(r);

        Auction a = new AuctionClosingPolicy(auctionDuration, maxBids).open(r.id(), OffsetDateTime.now());
        auctions.save(a);

        audit.record(tenant.profileId(), org.id(), republishedFrom == null ? "REQUEST_PUBLISHED" : "REQUEST_REPUBLISHED",
                "shipment_request", r.id(), republishedFrom == null ? null : "desde=" + republishedFrom);
        return summaries.of(r, false);
    }
}
