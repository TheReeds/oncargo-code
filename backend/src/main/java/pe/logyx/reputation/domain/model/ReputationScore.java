package pe.logyx.reputation.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Reputación agregada de una organización. Promedios null si aún no tiene calificaciones. */
public record ReputationScore(UUID orgId, int totalReviews, BigDecimal avgRating, BigDecimal avgPunctuality,
                              BigDecimal avgCargoCondition, BigDecimal avgCommunication, BigDecimal trustScore,
                              OffsetDateTime updatedAt) {

    public static ReputationScore initial(UUID orgId, BigDecimal priorMean) {
        return new ReputationScore(orgId, 0, null, null, null, null, priorMean, null);
    }
}
