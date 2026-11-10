package pe.logyx.reputation.domain;

import pe.logyx.reputation.domain.model.ReviewDirection;
import org.junit.jupiter.api.Test;
import pe.logyx.reputation.domain.model.Review;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrustScoreCalculatorTest {

    private static final BigDecimal M = BigDecimal.valueOf(75);

    private static List<BigDecimal> ratings(String... r) {
        return java.util.Arrays.stream(r).map(BigDecimal::new).toList();
    }

    @Test
    void sinCalificacionesEsElPrior() {
        assertEquals(0, new BigDecimal("75.0").compareTo(TrustScoreCalculator.trustScore(List.of(), M, 5)));
    }

    @Test
    void pocasCalificacionesMuevenPocoElScore() {
        // una sola nota de 1 (x=0): (5·75 + 0)/6 = 62.5 — no se desploma a 0
        assertEquals(new BigDecimal("62.5"), TrustScoreCalculator.trustScore(ratings("1"), M, 5));
        // una de 5 (x=100): (375 + 100)/6 = 79.2
        assertEquals(new BigDecimal("79.2"), TrustScoreCalculator.trustScore(ratings("5"), M, 5));
    }

    @Test
    void muchasCalificacionesConvergenAlPromedioReal() {
        List<BigDecimal> hundredFives = Collections.nCopies(100, new BigDecimal("5"));
        // (375 + 10000)/105 = 98.8
        assertEquals(new BigDecimal("98.8"), TrustScoreCalculator.trustScore(hundredFives, M, 5));
        // C = 0 → promedio exacto: 3 → 50
        assertEquals(new BigDecimal("50.0"), TrustScoreCalculator.trustScore(ratings("3", "3"), M, 0));
    }

    @Test
    void scoreAgregaPromediosPorDimension() {
        UUID p = UUID.randomUUID(), c = UUID.randomUUID();
        Review r1 = Review.write(UUID.randomUUID(), p, c, p, null, 5, 4, 5, "ok");
        Review r2 = Review.write(UUID.randomUUID(), p, c, p, null, 3, 4, 2, null);
        var s = TrustScoreCalculator.score(c, List.of(r1, r2), M, 5, OffsetDateTime.now());
        assertEquals(2, s.totalReviews());
        assertEquals(new BigDecimal("4.00"), s.avgPunctuality());
        assertEquals(new BigDecimal("4.00"), s.avgCargoCondition());
        assertEquals(new BigDecimal("3.50"), s.avgCommunication());
        assertEquals(new BigDecimal("3.84"), s.avgRating());   // (4.67 + 3.00)/2
        assertEquals(ReviewDirection.PYME_TO_CARRIER, r1.direction());
        assertEquals(c, r1.reviewedOrgId());
        assertNull(TrustScoreCalculator.score(c, List.of(), M, 5, OffsetDateTime.now()).avgRating());
    }
}
