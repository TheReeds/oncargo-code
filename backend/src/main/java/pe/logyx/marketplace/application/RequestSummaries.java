package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Arma {@link RequestSummary}/{@link BidSummary} para las consultas y casos de uso del marketplace. */
@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class RequestSummaries {

    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject OrganizationDirectory organizations;
    @Inject TenantContext tenant;
    @Inject SystemTx system;

    public RequestSummary of(ShipmentRequest r, boolean includeMyBid) {
        return of(List.of(r), includeMyBid).getFirst();
    }

    public List<RequestSummary> of(List<ShipmentRequest> list, boolean includeMyBid) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(ShipmentRequest::id).toList();
        Map<UUID, Auction> byRequest = auctions.listByRequests(ids).stream()
                .collect(Collectors.toMap(Auction::requestId, Function.identity()));
        Map<UUID, Long> counts = bidCounts(byRequest.values().stream().map(Auction::id).toList());
        Map<UUID, Organization> requesters = organizations.byIds(
                list.stream().map(ShipmentRequest::requesterOrgId).collect(Collectors.toSet()));
        Map<UUID, BidSummary> myBids = includeMyBid
                ? bids(bids.listByRequestsAndCarrier(ids, tenant.orgId())).stream()
                    .collect(Collectors.toMap(b -> b.bid().requestId(), Function.identity()))
                : Map.of();
        return list.stream().map(r -> {
            Auction a = byRequest.get(r.id());
            Organization o = requesters.get(r.requesterOrgId());
            return new RequestSummary(r, a, a == null ? 0 : counts.getOrDefault(a.id(), 0L),
                    o == null ? null : o.legalName(), myBids.get(r.id()),
                    o != null && o.rucVerified());
        }).toList();
    }

    public List<BidSummary> bids(List<Bid> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, Organization> carriers = organizations.byIds(
                list.stream().map(Bid::carrierOrgId).collect(Collectors.toSet()));
        return list.stream().map(b -> {
            Organization c = carriers.get(b.carrierOrgId());
            return new BidSummary(b, c.legalName(), c.rucVerified());
        }).toList();
    }

    /**
     * Número de ofertas por subasta. Un transportista no puede leer las ofertas
     * ajenas (política RLS bids_carrier) pero sí debe saber cuántas hay: el
     * conteo agregado se calcula en contexto de sistema y no expone montos ni
     * competidores.
     */
    private Map<UUID, Long> bidCounts(List<UUID> auctionIds) {
        if (auctionIds.isEmpty()) return Map.of();
        return tenant.orgType() == OrgType.CARRIER
                ? system.call(() -> bids.countByAuctions(auctionIds))
                : bids.countByAuctions(auctionIds);
    }
}
