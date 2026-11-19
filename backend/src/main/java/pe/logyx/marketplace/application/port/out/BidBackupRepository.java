package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.domain.model.BidBackup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidBackupRepository {
    /** Visible para la PYME dueña, para el transportista (solo si ya se le pidió confirmar) y el operador. */
    Optional<BidBackup> findByBid(UUID bidId);
    /** Por posición ascendente. Las de la PYME dueña. */
    List<BidBackup> listByRequest(UUID requestId);
    /** Lo que se le pidió confirmar al transportista (REQUESTED en adelante), más recientes primero. */
    List<BidBackup> listRequestedForCarrier(UUID carrierOrgId);
    Collection<BidBackup> listByRequests(Collection<UUID> requestIds);
    void save(BidBackup backup);
}
