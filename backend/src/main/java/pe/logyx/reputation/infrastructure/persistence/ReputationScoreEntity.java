package pe.logyx.reputation.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.reputation.domain.model.ReputationScore;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reputation_scores")
public class ReputationScoreEntity extends PanacheEntityBase {

    @Id
    @Column(name = "org_id")
    public UUID orgId;

    @Column(name = "total_reviews")
    public int totalReviews;

    @Column(name = "avg_rating")
    public BigDecimal avgRating;

    @Column(name = "avg_punctuality")
    public BigDecimal avgPunctuality;

    @Column(name = "avg_cargo_condition")
    public BigDecimal avgCargoCondition;

    @Column(name = "avg_communication")
    public BigDecimal avgCommunication;

    @Column(name = "trust_score")
    public BigDecimal trustScore;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    ReputationScore toDomain() {
        return new ReputationScore(orgId, totalReviews, avgRating, avgPunctuality, avgCargoCondition,
                avgCommunication, trustScore, updatedAt);
    }

    void apply(ReputationScore s) {
        orgId = s.orgId();
        totalReviews = s.totalReviews();
        avgRating = s.avgRating();
        avgPunctuality = s.avgPunctuality();
        avgCargoCondition = s.avgCargoCondition();
        avgCommunication = s.avgCommunication();
        trustScore = s.trustScore();
        updatedAt = s.updatedAt();
    }
}
