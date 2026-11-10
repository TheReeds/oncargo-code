package pe.logyx.reputation.infrastructure.web;

import pe.logyx.reputation.domain.model.ReviewDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.reputation.application.ReputationProfile;
import pe.logyx.reputation.application.ReviewSummary;
import pe.logyx.reputation.application.ShipmentReviews;
import pe.logyx.reputation.domain.model.ReputationScore;
import pe.logyx.reputation.domain.model.Review;
import pe.logyx.shared.tenancy.OrgType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReputationDtos {
    private ReputationDtos() {}

    /** Dimensiones 1–5. Transportista → PYME: cargoCondition = exactitud de la carga declarada. */
    public record SubmitReview(@NotNull @Min(1) @Max(5) Integer punctuality,
                               @NotNull @Min(1) @Max(5) Integer cargoCondition,
                               @NotNull @Min(1) @Max(5) Integer communication,
                               @Size(max = 1000) String comment) {}

    public record ReviewView(UUID id, UUID shipmentId, UUID reviewerOrgId, String reviewerName, UUID reviewedOrgId,
                             ReviewDirection direction, int punctuality, int cargoCondition, int communication,
                             BigDecimal rating, String comment, OffsetDateTime createdAt) {
        static ReviewView of(ReviewSummary s) {
            Review r = s.review();
            return new ReviewView(r.id(), r.shipmentId(), r.reviewerOrgId(), s.reviewerName(), r.reviewedOrgId(),
                    r.direction(), r.punctuality(), r.cargoCondition(), r.communication(), r.rating(), r.comment(),
                    r.createdAt());
        }
    }

    /** {@code blockedCode}/{@code blockedReason}: por qué aún no se puede calificar (LOGYX-REV-001/002/003). */
    public record ShipmentReviewsView(boolean canReview, String blockedCode, String blockedReason,
                                      List<ReviewView> reviews) {
        static ShipmentReviewsView of(ShipmentReviews s) {
            return new ShipmentReviewsView(s.canReview(), s.blocker() == null ? null : s.blocker().code(),
                    s.blocker() == null ? null : s.blocker().message(),
                    s.reviews().stream().map(ReviewView::of).toList());
        }
    }

    /**
     * Perfil público de reputación (RF44). {@code trustScore} 0–100 bayesiano (RN09):
     * (priorWeight·priorMean + Σ (rating−1)·25) / (priorWeight + totalReviews). Promedios null sin calificaciones.
     */
    public record ReputationView(UUID organizationId, String legalName, OrgType type, boolean rucVerified,
                                 String city, BigDecimal trustScore, int totalReviews, BigDecimal averageRating,
                                 BigDecimal averagePunctuality, BigDecimal averageCargoCondition,
                                 BigDecimal averageCommunication, BigDecimal priorMean, int priorWeight,
                                 List<ReviewView> recentReviews) {
        static ReputationView of(ReputationProfile p) {
            Organization o = p.organization();
            ReputationScore s = p.score();
            return new ReputationView(o.id(), o.legalName(), o.type(), o.rucVerified(), o.city(), s.trustScore(),
                    s.totalReviews(), s.avgRating(), s.avgPunctuality(), s.avgCargoCondition(), s.avgCommunication(),
                    p.priorMean(), p.priorWeight(), p.recentReviews().stream().map(ReviewView::of).toList());
        }
    }
}
