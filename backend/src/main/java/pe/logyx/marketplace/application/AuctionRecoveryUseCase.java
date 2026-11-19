package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * F07: qué hace la PYME cuando su subasta no prospera.
 * <ul>
 *   <li><b>Reabrir</b>: la subasta cerró por tiempo (RN04) y la solicitud sigue publicada; vuelve a abrir por
 *       {@code logyx.auction.duration}, opcionalmente con otra fecha de recojo (nunca pasada).</li>
 *   <li><b>Republicar</b>: la solicitud está cancelada (por la PYME o porque se canceló el envío adjudicado, F06);
 *       se crea una solicitud nueva con la misma carga, precio recalculado y subasta nueva.</li>
 * </ul>
 */
@ApplicationScoped
public class AuctionRecoveryUseCase {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject PublishRequestUseCase publish;
    @Inject RequestSummaries summaries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    @ConfigProperty(name = "logyx.auction.duration") Duration auctionDuration;

    @Transactional
    public RequestSummary reopen(UUID requestId, LocalDate pickupDate) {
        ShipmentRequest r = requests.findOwned(requestId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        Auction a = auctions.findByRequestForUpdate(r.id()).orElseThrow();
        if (!r.isPublished()) {
            throw BusinessException.conflict("LOGYX-AUC-003", "Solo se reabre la subasta de una solicitud publicada");
        }
        OffsetDateTime now = OffsetDateTime.now();
        a.reopen(now, auctionDuration);
        r.reschedule(pickupDate, LocalDate.now(LIMA));
        requests.save(r);
        auctions.save(a);
        audit.record(tenant.profileId(), tenant.orgId(), "AUCTION_REOPENED", "shipment_request", r.id(),
                "cierra=" + a.closesAt());
        return summaries.of(r, false);
    }

    @Transactional
    public RequestSummary republish(UUID requestId, LocalDate pickupDate) {
        ShipmentRequest old = requests.findOwned(requestId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (!old.isCancelled()) {
            throw BusinessException.conflict("LOGYX-REQ-004", "Solo se republica una solicitud cancelada");
        }
        var c = old.cargo();
        LocalDate date = pickupDate == null ? c.pickupDate() : pickupDate;
        if (date.isBefore(LocalDate.now(LIMA))) {
            throw BusinessException.badRequest("LOGYX-REQ-003",
                    "La fecha de recojo ya pasó: indica una nueva fecha", "pickupDate");
        }
        return publish.publish(new PublishRequestCommand(c.originCity().name(), c.originAddress(),
                c.destinationCity().name(), c.destinationAddress(), c.cargoType(), c.cargoDescription(), c.weightKg(),
                c.volumeM3(), date, old.details(), null, null, null), old.id());
    }
}
