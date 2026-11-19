package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.application.OrganizationGuard;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.event.RequestPublished;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.AuctionClosingPolicy;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.places.application.PlaceService;
import pe.logyx.pricing.application.PricingService;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.idempotency.IdempotencyKeys;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

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
    @Inject RequestCodes codes;
    @Inject IdempotencyKeys idempotency;
    @Inject PlaceService places;
    @Inject Event<RequestPublished> requestPublished;
    @Inject InviteCarriersUseCase invitations;

    /** Ámbito de {@code idempotency_keys} para esta escritura. */
    public static final String IDEMPOTENCY_SCOPE = "shipment_request";

    /** La solicitud y si es la ya creada por una petición anterior con la misma {@code Idempotency-Key}. */
    public record Published(RequestSummary summary, boolean replayed) {}

    /** RN04 (2.14): duración y máximo de ofertas vigentes (parámetros del operador; sin cambio rige la configuración). */
    @Inject pe.logyx.shared.config.SystemParameters parameters;

    @Transactional
    public RequestSummary publish(PublishRequestCommand cmd) {
        return publish(cmd, null);
    }

    /**
     * Publica con protección contra doble publicación: con {@code Idempotency-Key}, una segunda petición con la misma
     * clave (doble clic, reintento de red) devuelve la solicitud ya creada en las últimas 24 h en vez de crear otra.
     */
    @Transactional
    public Published publishOnce(PublishRequestCommand cmd) {
        String key = cmd.idempotencyKey();
        if (key == null) return new Published(publish(cmd, null), false);
        idempotency.lock(IDEMPOTENCY_SCOPE, key);   // serializa los reintentos simultáneos con la misma clave
        var previous = idempotency.find(IDEMPOTENCY_SCOPE, key);
        if (previous.isPresent()) {
            var existing = requests.findOwned(previous.get(), tenant.orgId());
            if (existing.isPresent()) return new Published(summaries.of(existing.get(), false), true);
        }
        RequestSummary created = publish(cmd, null);
        idempotency.remember(IDEMPOTENCY_SCOPE, key, created.request().id());
        return new Published(created, false);
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
                new ShipmentRequest.Pricing(quote.distanceKm(), quote.suggestedPrice(), quote.priceFloor(),
                        quote.tariffVersion()),
                republishedFrom, codes.next(), cmd.details());
        requests.save(r);
        var used = new ArrayList<UUID>();
        used.add(cmd.originPlaceId());
        used.add(cmd.destinationPlaceId());
        places.markUsed(used);                                                             // la libreta ordena por uso

        Auction a = new AuctionClosingPolicy(parameters.auctionDuration(), parameters.auctionMaxBids()).open(r.id(), OffsetDateTime.now());
        auctions.save(a);

        audit.record(tenant.profileId(), org.id(), republishedFrom == null ? "REQUEST_PUBLISHED" : "REQUEST_REPUBLISHED",
                "shipment_request", r.id(), (republishedFrom == null ? "" : "desde=" + republishedFrom + " ") + "codigo=" + r.code());
        if (cmd.invitePreferred()) invitations.invite(r);                                  // roadmap 2.9
        requestPublished.fire(new RequestPublished(r.id(), org.id(), r.routeLabel(), origin.name(), destination.name()));
        return summaries.of(r, false);
    }
}
