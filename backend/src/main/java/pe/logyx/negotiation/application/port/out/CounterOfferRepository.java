package pe.logyx.negotiation.application.port.out;

import pe.logyx.negotiation.domain.model.CounterOffer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CounterOfferRepository {
    Optional<CounterOffer> findById(UUID id);
    /** Más recientes primero. */
    List<CounterOffer> listByBid(UUID bidId);
    boolean hasPending(UUID bidId);
    /** Inserta o actualiza. */
    void save(CounterOffer counterOffer);
}
