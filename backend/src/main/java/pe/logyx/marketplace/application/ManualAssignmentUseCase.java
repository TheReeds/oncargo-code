package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.BidEligibilityPolicy;
import pe.logyx.marketplace.domain.ManualAssignmentPolicy;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RN10: el operador asigna manualmente un transportista a una solicitud con SLA vencido. Equivale a que el
 * transportista ofertara y la PYME aceptara: se registra una oferta marcada {@code assignedByOperator}
 * (por el precio sugerido salvo que se indique otro, nunca bajo el piso RN03) y se adjudica con el mismo
 * flujo de {@link AcceptBidUseCase} (RN05 + envío). Corre con RLS del operador (políticas de V9).
 */
@ApplicationScoped
public class ManualAssignmentUseCase {

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject FleetService fleet;
    @Inject OrganizationDirectory organizations;
    @Inject AcceptBidUseCase acceptance;

    @ConfigProperty(name = "logyx.operator.sla", defaultValue = "PT24H")
    Duration sla;

    @Transactional
    public AcceptOutcome assign(UUID requestId, ManualAssignmentCommand cmd) {
        ShipmentRequest r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        Auction a = auctions.findByRequestForUpdate(r.id()).orElseThrow();
        long bidCount = bids.countByAuctions(List.of(a.id())).getOrDefault(a.id(), 0L);   // operador lee todas
        ManualAssignmentPolicy.ensureSlaBreached(r, bidCount, OffsetDateTime.now(), sla);

        if (organizations.byIds(List.of(cmd.carrierOrgId())).values().stream()
                .noneMatch(o -> o.type() == OrgType.CARRIER)) {
            throw BusinessException.badRequest("LOGYX-OPS-002", "La organización indicada no es un transportista",
                    "carrierOrgId");
        }
        Vehicle v = fleet.vehicleOfCarrier(cmd.carrierOrgId(), cmd.vehicleId());
        BidEligibilityPolicy.ensureVehicleEligible(v.isAvailable(), v.maxWeightKg(), r.cargo().weightKg());   // RN02
        BigDecimal amount = cmd.amount() == null ? r.pricing().suggestedPrice() : cmd.amount();
        BidEligibilityPolicy.ensureAboveFloor(amount, r.pricing().priceFloor());                             // RN03

        String notes = cmd.notes() == null || cmd.notes().isBlank()
                ? "Asignación manual del operador LOGYX (RN10)" : cmd.notes().trim();
        Bid bid = Bid.assignByOperator(a, r, cmd.carrierOrgId(), v, amount, notes);
        bids.save(bid);
        return acceptance.award(r, bid, "MANUAL_ASSIGNMENT");
    }
}
