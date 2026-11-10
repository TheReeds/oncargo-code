package pe.logyx.reputation.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.reputation.application.port.out.ReviewRepository;
import pe.logyx.reputation.domain.model.Review;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheReviewRepository implements ReviewRepository {

    @Override
    public List<Review> listByShipment(UUID shipmentId) {
        return map(ReviewEntity.list("shipmentId = ?1 order by createdAt", shipmentId));
    }

    @Override
    public boolean exists(UUID shipmentId, UUID reviewerOrgId) {
        return ReviewEntity.count("shipmentId = ?1 and reviewerOrgId = ?2", shipmentId, reviewerOrgId) > 0;
    }

    @Override
    public List<Review> listReceived(UUID reviewedOrgId) {
        return map(ReviewEntity.list("reviewedOrgId = ?1 order by createdAt", reviewedOrgId));
    }

    @Override
    public Paged<Review> pageReceived(UUID reviewedOrgId, PageRequest page) {
        var q = ReviewEntity.<ReviewEntity>find("reviewedOrgId = ?1 order by createdAt desc, id", reviewedOrgId)
                .page(page.page(), page.size());
        return new Paged<>(map(q.list()), page, ReviewEntity.count("reviewedOrgId", reviewedOrgId));
    }

    @Override
    public void insert(Review review) {
        ReviewEntity.from(review).persist();
    }

    private static List<Review> map(List<ReviewEntity> list) {
        return list.stream().map(ReviewEntity::toDomain).toList();
    }
}
