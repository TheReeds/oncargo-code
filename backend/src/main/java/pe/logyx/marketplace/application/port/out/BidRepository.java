package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.domain.model.Bid;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository {
    Optional<Bid> findForRequester(UUID bidId, UUID requesterOrgId);
    /** Por monto ascendente y luego por antigüedad. */
    List<Bid> listByRequest(UUID requestId);
    List<Bid> listPendingByRequest(UUID requestId);
    /** Más recientes primero. */
    List<Bid> listByCarrier(UUID carrierOrgId);
    List<Bid> listByRequestsAndCarrier(Collection<UUID> requestIds, UUID carrierOrgId);
    boolean existsForCarrier(UUID auctionId, UUID carrierOrgId);
    long countByAuctionExcludingCarrier(UUID auctionId, UUID carrierOrgId);
    Map<UUID, Long> countByAuctions(Collection<UUID> auctionIds);
    /** Inserta o actualiza. */
    void save(Bid bid);
}
