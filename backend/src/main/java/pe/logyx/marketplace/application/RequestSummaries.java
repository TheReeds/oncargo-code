package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.domain.BidStandingPolicy;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.reputation.application.ReputationQueries;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
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
    @Inject ReputationQueries reputation;

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
        Map<UUID, BigDecimal> trust = reputation.trustScores(requesters.keySet());
        Map<UUID, BidSummary> best = bestBids(list);
        Map<UUID, BidStanding> standings = standings(list, byRequest, myBids);
        return list.stream().map(r -> {
            Auction a = byRequest.get(r.id());
            Organization o = requesters.get(r.requesterOrgId());
            return new RequestSummary(r, a, a == null ? 0 : counts.getOrDefault(a.id(), 0L),
                    o == null ? null : o.legalName(), myBids.get(r.id()),
                    o != null && o.rucVerified(), trust.get(r.requesterOrgId()), best.get(r.id()),
                    standings.get(r.id()), canSeePrivate(r));
        }).toList();
    }

    /**
     * Datos privados de la solicitud (referencia, contactos, ventanas, instrucciones): la PYME dueña, el operador y el
     * transportista adjudicado cuando ya se adjudicó. Nunca los transportistas que solo miran o que ofertaron y perdieron.
     */
    private boolean canSeePrivate(ShipmentRequest r) {
        return r.isOwnedBy(tenant.orgId()) || tenant.orgType() == OrgType.OPERATOR
                || (r.status() == ShipmentRequest.Status.MATCHED && tenant.orgId().equals(r.assignedCarrierOrgId()));
    }

    /** Mejor oferta pendiente por solicitud, solo para la PYME dueña (en la misma consulta, sin N+1). */
    private Map<UUID, BidSummary> bestBids(List<ShipmentRequest> list) {
        if (tenant.orgType() != OrgType.PYME) return Map.of();
        List<UUID> owned = list.stream().filter(r -> r.isOwnedBy(tenant.orgId())).map(ShipmentRequest::id).toList();
        Map<UUID, Bid> best = bids.bestPendingByRequests(owned);
        if (best.isEmpty()) return Map.of();
        Map<UUID, BidSummary> byBid = bids(List.copyOf(best.values())).stream()
                .collect(Collectors.toMap(b -> b.bid().id(), Function.identity()));
        return best.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> byBid.get(e.getValue().id())));
    }

    /**
     * Posición del transportista en las solicitudes donde ofertó (RN04: ofertas selladas). La RLS de {@code bids} solo
     * deja ver las propias, así que los agregados se calculan en contexto de sistema y de ahí sale únicamente la
     * posición, el conteo y una banda cualitativa: nunca el monto de otra oferta.
     */
    private Map<UUID, BidStanding> standings(List<ShipmentRequest> list, Map<UUID, Auction> auctions,
                                             Map<UUID, BidSummary> myBids) {
        if (tenant.orgType() != OrgType.CARRIER || myBids.isEmpty()) return Map.of();
        List<UUID> ids = myBids.values().stream().filter(b -> b.bid().status() != Bid.Status.WITHDRAWN)
                .map(b -> b.bid().requestId()).toList();
        if (ids.isEmpty()) return Map.of();
        UUID me = tenant.orgId();
        // Sistema: un transportista no puede leer las ofertas ajenas; solo se extraen agregados.
        Map<UUID, BidRepository.Ranking> rankings = system.call(() -> bids.rankings(ids, me));
        Map<UUID, ShipmentRequest> byId = list.stream().collect(Collectors.toMap(ShipmentRequest::id, Function.identity()));
        Map<UUID, BidStanding> out = new java.util.HashMap<>();
        for (UUID id : ids) {
            BidRepository.Ranking rk = rankings.get(id);
            if (rk == null) continue;
            ShipmentRequest r = byId.get(id);
            Auction a = auctions.get(id);
            boolean decided = r.status() != ShipmentRequest.Status.PUBLISHED;
            boolean closed = decided || a == null || !a.isOpen();
            int rank = rk.betterCount() + 1;
            var band = decided ? null
                    : BidStandingPolicy.band(myBids.get(id).bid().amount(), rk.bestAmount());
            out.put(id, new BidStanding(rank, rk.bidCount(), rank == 1, band, closed));
        }
        return out;
    }

    public List<BidSummary> bids(List<Bid> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, Organization> carriers = organizations.byIds(
                list.stream().map(Bid::carrierOrgId).collect(Collectors.toSet()));
        Map<UUID, BigDecimal> trust = reputation.trustScores(carriers.keySet());
        return list.stream().map(b -> {
            Organization c = carriers.get(b.carrierOrgId());
            return new BidSummary(b, c.legalName(), c.rucVerified(), trust.get(b.carrierOrgId()));
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
