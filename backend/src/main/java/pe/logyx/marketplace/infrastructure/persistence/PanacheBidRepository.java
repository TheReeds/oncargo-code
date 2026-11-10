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
    public List<Bid> listByRequestsAndCarrier(Collection<UUID> requestIds, UUID carrierOrgId) {
        if (requestIds.isEmpty()) return List.of();
        return map(BidEntity.list("requestId in ?1 and carrierOrgId = ?2", requestIds, carrierOrgId));
    }

    @Override
    public boolean existsForCarrier(UUID auctionId, UUID carrierOrgId) {
        return BidEntity.count("auctionId = ?1 and carrierOrgId = ?2", auctionId, carrierOrgId) > 0;
    }

    @Override
    public long countByAuctionExcludingCarrier(UUID auctionId, UUID carrierOrgId) {
        return BidEntity.count("auctionId = ?1 and carrierOrgId <> ?2", auctionId, carrierOrgId);
    }

    @Override
    public Map<UUID, Long> countByAuctions(Collection<UUID> auctionIds) {
        if (auctionIds.isEmpty()) return Map.of();
        return BidEntity.getEntityManager()
                .createQuery("select b.auctionId, count(b) from BidEntity b where b.auctionId in :ids "
                        + "group by b.auctionId", Object[].class)
                .setParameter("ids", auctionIds)
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

    private static List<Bid> map(List<BidEntity> list) {
        return list.stream().map(BidEntity::toDomain).toList();
    }
}
