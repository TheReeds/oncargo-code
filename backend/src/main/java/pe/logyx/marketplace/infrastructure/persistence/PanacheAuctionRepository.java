package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.domain.model.Auction;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheAuctionRepository implements AuctionRepository {

    @Override
    public Optional<Auction> findByRequest(UUID requestId) {
        return AuctionEntity.<AuctionEntity>find("requestId", requestId).firstResultOptional()
                .map(AuctionEntity::toDomain);
    }

    @Override
    public Optional<Auction> findByRequestForUpdate(UUID requestId) {
        return AuctionEntity.<AuctionEntity>find("requestId", requestId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional().map(AuctionEntity::toDomain);
    }

    @Override
    public List<Auction> listByRequests(Collection<UUID> requestIds) {
        if (requestIds.isEmpty()) return List.of();
        return AuctionEntity.<AuctionEntity>list("requestId in ?1", requestIds).stream()
                .map(AuctionEntity::toDomain).toList();
    }

    @Override
    public List<Auction> listOpenClosingBefore(OffsetDateTime now) {
        return AuctionEntity.<AuctionEntity>list("status = ?1 and closesAt <= ?2", Auction.Status.OPEN, now).stream()
                .map(AuctionEntity::toDomain).toList();
    }

    @Override
    public void save(Auction auction) {
        AuctionEntity e = AuctionEntity.findById(auction.id());
        if (e == null) {
            e = new AuctionEntity();
            e.apply(auction);
            e.persist();
        } else {
            e.apply(auction);
        }
    }
}
