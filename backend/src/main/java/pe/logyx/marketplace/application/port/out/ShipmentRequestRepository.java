package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.application.MarketplaceFilter;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRequestRepository {
    Optional<ShipmentRequest> findById(UUID id);
    Optional<ShipmentRequest> findOwned(UUID id, UUID requesterOrgId);
    /** Más recientes primero. */
    List<ShipmentRequest> listByRequester(UUID requesterOrgId);
    /** Publicadas con subasta abierta que cumplen el filtro, ordenadas y paginadas según {@code filter.sort()}. */
    Paged<ShipmentRequest> searchOpenMarketplace(MarketplaceFilter filter, PageRequest page);
    /** Cantidad de solicitudes publicadas con subasta abierta de la PYME dada. */
    long countPublishedByRequester(UUID requesterOrgId);
    /** Más recientes primero. */
    List<ShipmentRequest> listByIds(Collection<UUID> ids);
    /** Inserta o actualiza. */
    void save(ShipmentRequest request);
}
