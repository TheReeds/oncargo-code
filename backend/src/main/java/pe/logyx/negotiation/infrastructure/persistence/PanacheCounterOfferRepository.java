package pe.logyx.negotiation.infrastructure.persistence;

import pe.logyx.negotiation.domain.model.CounterOfferStatus;
import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.negotiation.application.port.out.CounterOfferRepository;
import pe.logyx.negotiation.domain.model.CounterOffer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheCounterOfferRepository implements CounterOfferRepository {

    @Override
    public Optional<CounterOffer> findById(UUID id) {
        return CounterOfferEntity.<CounterOfferEntity>findByIdOptional(id).map(CounterOfferEntity::toDomain);
    }

    @Override
    public List<CounterOffer> listByBid(UUID bidId) {
        return CounterOfferEntity.<CounterOfferEntity>list("bidId = ?1 order by createdAt desc", bidId).stream()
                .map(CounterOfferEntity::toDomain).toList();
    }

    @Override
    public boolean hasPending(UUID bidId) {
        return CounterOfferEntity.count("bidId = ?1 and status = ?2", bidId, CounterOfferStatus.PENDING) > 0;
    }

    @Override
    public Optional<CounterOffer> findPending(UUID bidId) {
        return CounterOfferEntity.<CounterOfferEntity>find("bidId = ?1 and status = ?2", bidId,
                CounterOfferStatus.PENDING).firstResultOptional().map(CounterOfferEntity::toDomain);
    }

    @Override
    public List<CounterOffer> listPendingByRequest(UUID requestId) {
        return CounterOfferEntity.<CounterOfferEntity>list("requestId = ?1 and status = ?2", requestId,
                CounterOfferStatus.PENDING).stream().map(CounterOfferEntity::toDomain).toList();
    }

    @Override
    public void flush() {
        CounterOfferEntity.getEntityManager().flush();
    }

    @Override
    public void save(CounterOffer c) {
        CounterOfferEntity e = CounterOfferEntity.findById(c.id());
        if (e == null) {
            e = new CounterOfferEntity();
            e.apply(c);
            e.persist();
        } else {
            e.apply(c);
        }
    }
}
