package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheBidRepository implements BidRepository {

    @Override
    public Optional<Bid> findById(UUID bidId) {
        return BidEntity.<BidEntity>findByIdOptional(bidId).map(BidEntity::toDomain);
    }

    @Override
    public Optional<Bid> findForRequester(UUID bidId, UUID requesterOrgId) {
        return BidEntity.<BidEntity>find("id = ?1 and requesterOrgId = ?2", bidId, requesterOrgId)
                .firstResultOptional().map(BidEntity::toDomain);
    }

    @Override
    public List<Bid> listByRequest(UUID requestId) {
        return map(BidEntity.list("requestId = ?1 order by amount asc, createdAt asc", requestId));
    }

    @Override
    public List<Bid> listPendingByRequest(UUID requestId) {
        return map(BidEntity.list("requestId = ?1 and status = ?2", requestId, Bid.Status.PENDING));
    }

    @Override
    public List<Bid> listByCarrier(UUID carrierOrgId) {
        return map(BidEntity.list("carrierOrgId = ?1 order by createdAt desc", carrierOrgId));
    }

    @Override
    public List<Bid> listPendingByVehicle(UUID vehicleId) {
        return map(BidEntity.list("vehicleId = ?1 and status = ?2 order by createdAt", vehicleId, Bid.Status.PENDING));
    }

    @Override
    public List<Bid> listByRequestsAndCarrier(Collection<UUID> requestIds, UUID carrierOrgId) {
        if (requestIds.isEmpty()) return List.of();
        return map(BidEntity.list("requestId in ?1 and carrierOrgId = ?2", requestIds, carrierOrgId));
    }

    @Override
    public Optional<Bid> findForCarrier(UUID auctionId, UUID carrierOrgId) {
        return BidEntity.<BidEntity>find("auctionId = ?1 and carrierOrgId = ?2", auctionId, carrierOrgId)
                .firstResultOptional().map(BidEntity::toDomain);
    }

    @Override
    public boolean existsForCarrier(UUID auctionId, UUID carrierOrgId) {
        return BidEntity.count("auctionId = ?1 and carrierOrgId = ?2", auctionId, carrierOrgId) > 0;
    }

    @Override
    public long countByAuctionExcludingCarrier(UUID auctionId, UUID carrierOrgId) {
        return BidEntity.count("auctionId = ?1 and carrierOrgId <> ?2 and status <> ?3", auctionId, carrierOrgId,
                Bid.Status.WITHDRAWN);
    }

    @Override
    public Map<UUID, Long> countByAuctions(Collection<UUID> auctionIds) {
        if (auctionIds.isEmpty()) return Map.of();
        return BidEntity.getEntityManager()
                .createQuery("select b.auctionId, count(b) from BidEntity b where b.auctionId in :ids "
                        + "and b.status <> :withdrawn group by b.auctionId", Object[].class)
                .setParameter("ids", auctionIds)
                .setParameter("withdrawn", Bid.Status.WITHDRAWN)
                .getResultList().stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    @Override
    public long countPendingToReview(UUID requesterOrgId) {
        return BidEntity.count("requesterOrgId = ?1 and status = ?2 and requestId in "
                        + "(select r.id from ShipmentRequestEntity r where r.status = ?3)",
                requesterOrgId, Bid.Status.PENDING, ShipmentRequest.Status.PUBLISHED);
    }

    @Override
    public long countByCarrierAndStatus(UUID carrierOrgId, Bid.Status status) {
        return BidEntity.count("carrierOrgId = ?1 and status = ?2", carrierOrgId, status);
    }

    @Override
    public Map<UUID, Bid> bestPendingByRequests(Collection<UUID> requestIds) {
        if (requestIds.isEmpty()) return Map.of();
        List<BidEntity> pending = BidEntity.list("requestId in ?1 and status = ?2 order by amount asc, createdAt asc",
                requestIds, Bid.Status.PENDING);
        Map<UUID, Bid> best = new java.util.HashMap<>();
        for (BidEntity e : pending) best.putIfAbsent(e.requestId, e.toDomain());   // la primera de cada solicitud es la menor
        return best;
    }

    @Override
    public Map<UUID, Ranking> rankings(Collection<UUID> requestIds, UUID carrierOrgId) {
        if (requestIds.isEmpty()) return Map.of();
        var em = BidEntity.getEntityManager();
        Map<UUID, Object[]> totals = new java.util.HashMap<>();
        for (Object[] row : em.createQuery("select b.requestId, count(b), min(b.amount) from BidEntity b "
                        + "where b.requestId in :ids and b.status <> :w group by b.requestId", Object[].class)
                .setParameter("ids", requestIds).setParameter("w", Bid.Status.WITHDRAWN).getResultList()) {
            totals.put((UUID) row[0], row);
        }
        Map<UUID, Long> better = new java.util.HashMap<>();
        for (Object[] row : em.createQuery("select m.requestId, count(o) from BidEntity m, BidEntity o "
                        + "where m.requestId in :ids and m.carrierOrgId = :me and o.requestId = m.requestId "
                        + "and o.id <> m.id and o.status <> :w and (o.amount < m.amount "
                        + "or (o.amount = m.amount and o.createdAt < m.createdAt)) group by m.requestId", Object[].class)
                .setParameter("ids", requestIds).setParameter("me", carrierOrgId).setParameter("w", Bid.Status.WITHDRAWN)
                .getResultList()) {
            better.put((UUID) row[0], (Long) row[1]);
        }
        Map<UUID, Ranking> out = new java.util.HashMap<>();
        totals.forEach((req, row) -> out.put(req, new Ranking(better.getOrDefault(req, 0L).intValue(),
                ((Long) row[1]).intValue(), (java.math.BigDecimal) row[2])));
        return out;
    }

    @Override
    public void save(Bid bid) {
        BidEntity e = BidEntity.findById(bid.id());
        if (e == null) {
            e = new BidEntity();
            e.apply(bid);
            e.persist();
        } else {
            e.apply(bid);
        }
    }

    @Override
    public void recordRevision(UUID bidId, UUID carrierOrgId, Bid.Revision r) {
        BidEntity.getEntityManager().createNativeQuery("INSERT INTO bid_revisions (bid_id, carrier_org_id, "
                        + "previous_amount, new_amount, previous_vehicle_id, new_vehicle_id, previous_plate, new_plate) "
                        + "VALUES (:bid, :org, :pa, :na, :pv, :nv, :pp, :np)")
                .setParameter("bid", bidId).setParameter("org", carrierOrgId)
                .setParameter("pa", r.previousAmount()).setParameter("na", r.newAmount())
                .setParameter("pv", r.previousVehicleId()).setParameter("nv", r.newVehicleId())
                .setParameter("pp", r.previousPlate()).setParameter("np", r.newPlate()).executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RevisionRow> listRevisions(UUID bidId) {
        List<Object[]> rows = BidEntity.getEntityManager().createNativeQuery("SELECT created_at, previous_amount, "
                        + "new_amount, previous_vehicle_id, new_vehicle_id, previous_plate, new_plate FROM bid_revisions "
                        + "WHERE bid_id = :bid ORDER BY created_at, id").setParameter("bid", bidId).getResultList();
        return rows.stream().map(r -> new RevisionRow(toOffset(r[0]),
                new Bid.Revision((java.math.BigDecimal) r[1], (java.math.BigDecimal) r[2], (UUID) r[3], (UUID) r[4],
                        (String) r[5], (String) r[6]))).toList();
    }

    private static java.time.OffsetDateTime toOffset(Object o) {
        if (o instanceof java.time.OffsetDateTime odt) return odt;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (o instanceof java.time.Instant i) return i.atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Fecha inesperada: " + o);
    }

    private static List<Bid> map(List<BidEntity> list) {
        return list.stream().map(BidEntity::toDomain).toList();
    }
}
