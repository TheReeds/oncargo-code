package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.domain.model.Auction;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionRepository {
    Optional<Auction> findByRequest(UUID requestId);
    /**
     * Con bloqueo pesimista (SELECT ... FOR UPDATE): serializa las ofertas
     * concurrentes de la misma subasta para que el conteo de RN04 sea exacto.
     */
    Optional<Auction> findByRequestForUpdate(UUID requestId);
    List<Auction> listByRequests(Collection<UUID> requestIds);
    List<Auction> listOpenClosingBefore(OffsetDateTime now);
    /** Inserta o actualiza. */
    void save(Auction auction);
}
