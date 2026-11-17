package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.domain.model.Bid;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository {
    /** Visible para el usuario actual (RLS: la PYME de la solicitud, el transportista dueño o el operador). */
    Optional<Bid> findById(UUID bidId);
    Optional<Bid> findForRequester(UUID bidId, UUID requesterOrgId);
    /** Por monto ascendente y luego por antigüedad. */
    List<Bid> listByRequest(UUID requestId);
    List<Bid> listPendingByRequest(UUID requestId);
    /** Más recientes primero. */
    List<Bid> listByCarrier(UUID carrierOrgId);
    List<Bid> listByRequestsAndCarrier(Collection<UUID> requestIds, UUID carrierOrgId);
    boolean existsForCarrier(UUID auctionId, UUID carrierOrgId);
    /** La oferta del transportista en la subasta (cualquier estado, incluida la retirada). */
    Optional<Bid> findForCarrier(UUID auctionId, UUID carrierOrgId);
    /** Sin contar las retiradas (F08). */
    long countByAuctionExcludingCarrier(UUID auctionId, UUID carrierOrgId);
    Map<UUID, Long> countByAuctions(Collection<UUID> auctionIds);
    /** Ofertas pendientes sobre solicitudes publicadas de la PYME (por revisar). */
    long countPendingToReview(UUID requesterOrgId);
    long countByCarrierAndStatus(UUID carrierOrgId, Bid.Status status);
    /** Inserta o actualiza. */
    void save(Bid bid);
}
