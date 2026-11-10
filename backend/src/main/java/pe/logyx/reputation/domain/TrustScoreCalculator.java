package pe.logyx.reputation.domain;

import pe.logyx.reputation.domain.model.ReputationScore;
import pe.logyx.reputation.domain.model.Review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * RN09 — Trust Score bayesiano (0–100) anclado a un prior de 75:
 * <pre>
 *   x_i   = (rating_i − 1) × 25          rating 1–5 (promedio de las 3 dimensiones) llevado a 0–100
 *   score = (C·m + Σ x_i) / (C + n)      m = 75 (prior), C = peso del prior (logyx.reputation.prior-weight, 5)
 * </pre>
 * Sin calificaciones el score es m; con pocas, se mueve poco (una sola nota de 1 con C=5 deja 62.5, no 0); con
 * muchas converge al promedio real. Se redondea a 1 decimal y se recalcula tras cada calificación.
 */
public final class TrustScoreCalculator {

    private TrustScoreCalculator() {}

    public static BigDecimal toPercent(BigDecimal rating) {
        return rating.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(25));
    }

    public static BigDecimal trustScore(List<BigDecimal> ratings, BigDecimal priorMean, int priorWeight) {
        BigDecimal sum = ratings.stream().map(TrustScoreCalculator::toPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = BigDecimal.valueOf(priorWeight);
        return c.multiply(priorMean).add(sum)
                .divide(c.add(BigDecimal.valueOf(ratings.size())), 1, RoundingMode.HALF_UP);
    }

    public static ReputationScore score(UUID orgId, List<Review> received, BigDecimal priorMean, int priorWeight,
                                        OffsetDateTime now) {
        if (received.isEmpty()) {
            return new ReputationScore(orgId, 0, null, null, null, null,
                    priorMean.setScale(1, RoundingMode.HALF_UP), now);
        }
        BigDecimal avgRating = received.stream().map(Review::rating).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(received.size()), 2, RoundingMode.HALF_UP);
        return new ReputationScore(orgId, received.size(), avgRating,
                avg(received, Review::punctuality), avg(received, Review::cargoCondition),
                avg(received, Review::communication),
                trustScore(received.stream().map(Review::rating).toList(), priorMean, priorWeight), now);
    }

    private static BigDecimal avg(List<Review> list, ToIntFunction<Review> f) {
        int sum = list.stream().mapToInt(f).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
    }
}
