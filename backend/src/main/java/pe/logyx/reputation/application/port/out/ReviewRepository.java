package pe.logyx.reputation.application.port.out;

import pe.logyx.reputation.domain.model.Review;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository {
    List<Review> listByShipment(UUID shipmentId);
    boolean exists(UUID shipmentId, UUID reviewerOrgId);
    List<Review> listReceived(UUID reviewedOrgId);
    /** RN09 (V23, 2.15): las de la organización y las de sus antecesoras (sucesora por cambio de RUC). */
    default List<Review> listReceivedWithPredecessors(UUID reviewedOrgId) { return listReceived(reviewedOrgId); }
    /** Más recientes primero. */
    Paged<Review> pageReceived(UUID reviewedOrgId, PageRequest page);
    void insert(Review review);
}
