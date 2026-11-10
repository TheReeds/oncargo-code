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
    /** Más recientes primero. */
    Paged<Review> pageReceived(UUID reviewedOrgId, PageRequest page);
    void insert(Review review);
}
