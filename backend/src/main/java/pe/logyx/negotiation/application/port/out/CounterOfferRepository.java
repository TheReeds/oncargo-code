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
    java.util.Optional<CounterOffer> findPending(UUID bidId);
    /** Pendientes de cualquier oferta de la solicitud. */
    List<CounterOffer> listPendingByRequest(UUID requestId);
    /** Inserta o actualiza. */
    void save(CounterOffer counterOffer);
    /** Escribe ya los cambios pendientes (el índice único de "una pendiente por oferta" se valida por fila). */
    void flush();
}
