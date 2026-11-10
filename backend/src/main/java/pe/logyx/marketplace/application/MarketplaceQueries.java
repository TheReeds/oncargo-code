package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
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
    @Inject FleetService fleet;
    @Inject TenantContext tenant;

    // ----- PYME

    public List<RequestSummary> myRequests() {
        return summaries.of(requests.listByRequester(tenant.orgId()), false);
    }

    /** Igual que {@link #myRequests()} pero paginado (los recientes primero). */
    public Paged<RequestSummary> myRequests(PageRequest page) {
        Paged<ShipmentRequest> p = Paged.slice(requests.listByRequester(tenant.orgId()), page);
        return new Paged<>(summaries.of(p.items(), false), page, p.total());
    }

    public List<BidSummary> bidsFor(UUID requestId) {
        ShipmentRequest r = requests.findOwned(requestId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        return summaries.bids(bids.listByRequest(r.id()));
    }

    // ----- Transportista

    /** Marketplace: solicitudes publicadas cuya subasta sigue abierta. */
    public Paged<RequestSummary> openMarketplace(MarketplaceFilter filter, PageRequest page) {
        Paged<ShipmentRequest> p = requests.searchOpenMarketplace(filter, page);
        return new Paged<>(summaries.of(p.items(), true), page, p.total());
    }

    /**
     * KPIs del dashboard. PYME: solicitudes activas y ofertas por revisar. Transportista: cargas abiertas
     * que caben en su vehículo disponible más grande (incluye las que ya ofertó) y ofertas pendientes.
     */
    public DashboardCounts dashboardCounts() {
        UUID org = tenant.orgId();
        if (tenant.orgType() == OrgType.CARRIER) {
            BigDecimal biggest = fleet.vehicles().stream().filter(Vehicle::isAvailable)
                    .map(Vehicle::maxWeightKg).max(BigDecimal::compareTo).orElse(null);
            long compatible = biggest == null ? 0 : requests.searchOpenMarketplace(
                    new MarketplaceFilter(null, null, null, null, biggest, null, MarketplaceFilter.Sort.PICKUP),
                    new PageRequest(0, 1)).total();
            return new DashboardCounts(0, 0, compatible, bids.countByCarrierAndStatus(org, Bid.Status.PENDING));
        }
        return new DashboardCounts(requests.countPublishedByRequester(org), bids.countPendingToReview(org), 0, 0);
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
