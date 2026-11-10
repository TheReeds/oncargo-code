package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRequestRepository {
    Optional<ShipmentRequest> findById(UUID id);
    Optional<ShipmentRequest> findOwned(UUID id, UUID requesterOrgId);
    /** Más recientes primero. */
    List<ShipmentRequest> listByRequester(UUID requesterOrgId);
    /** Publicadas con subasta abierta; por fecha de recojo y luego más recientes. */
    List<ShipmentRequest> listOpenMarketplace();
    /** Más recientes primero. */
    List<ShipmentRequest> listByIds(Collection<UUID> ids);
    /** Inserta o actualiza. */
    void save(ShipmentRequest request);
}
