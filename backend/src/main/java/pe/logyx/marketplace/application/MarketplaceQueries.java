package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Lecturas del marketplace para PYME, transportista y otros módulos. */
@ApplicationScoped
@Transactional
public class MarketplaceQueries {

    @Inject ShipmentRequestRepository requests;
    @Inject BidRepository bids;
    @Inject RequestSummaries summaries;
    @Inject TenantContext tenant;

    // ----- PYME

    public List<RequestSummary> myRequests() {
        return summaries.of(requests.listByRequester(tenant.orgId()), false);
    }

    public List<BidSummary> bidsFor(UUID requestId) {
        ShipmentRequest r = requests.findOwned(requestId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        return summaries.bids(bids.listByRequest(r.id()));
    }

    // ----- Transportista

    /** Marketplace: solicitudes publicadas cuya subasta sigue abierta. */
    public List<RequestSummary> openMarketplace() {
        return summaries.of(requests.listOpenMarketplace(), true);
    }

    public List<RequestSummary> myBidRequests() {
        List<UUID> ids = bids.listByCarrier(tenant.orgId()).stream().map(Bid::requestId).toList();
        if (ids.isEmpty()) return List.of();
        return summaries.of(requests.listByIds(ids), true);
    }

    // ----- Común

    public RequestSummary detail(UUID requestId) {
        ShipmentRequest r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        boolean isOwner = r.isOwnedBy(tenant.orgId());
        if (!isOwner && tenant.orgType() == OrgType.PYME) {
            throw BusinessException.notFound("Solicitud");
        }
        return summaries.of(r, !isOwner);
    }

    /** Para otros módulos (shipment): solicitudes visibles para el usuario actual. */
    public List<ShipmentRequest> requestsByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return requests.listByIds(ids);
    }
}
