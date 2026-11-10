package pe.logyx.reputation.infrastructure.persistence;

import pe.logyx.reputation.domain.model.ReviewDirection;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.reputation.domain.model.Review;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class ReviewEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "shipment_id")
    public UUID shipmentId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "reviewer_org_id")
    public UUID reviewerOrgId;

    @Column(name = "reviewed_org_id")
    public UUID reviewedOrgId;

    @Column(name = "reviewer_profile_id")
    public UUID reviewerProfileId;

    @Enumerated(EnumType.STRING)
    public ReviewDirection direction;

    public short punctuality;

    @Column(name = "cargo_condition")
    public short cargoCondition;

    public short communication;

    public BigDecimal rating;
    public String comment;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Review toDomain() {
        return new Review(id, shipmentId, requesterOrgId, carrierOrgId, reviewerOrgId, reviewedOrgId,
                reviewerProfileId, direction, punctuality, cargoCondition, communication, rating, comment, createdAt);
    }

    static ReviewEntity from(Review r) {
        ReviewEntity e = new ReviewEntity();
        e.id = r.id();
        e.shipmentId = r.shipmentId();
        e.requesterOrgId = r.requesterOrgId();
        e.carrierOrgId = r.carrierOrgId();
        e.reviewerOrgId = r.reviewerOrgId();
        e.reviewedOrgId = r.reviewedOrgId();
        e.reviewerProfileId = r.reviewerProfileId();
        e.direction = r.direction();
        e.punctuality = (short) r.punctuality();
        e.cargoCondition = (short) r.cargoCondition();
        e.communication = (short) r.communication();
        e.rating = r.rating();
        e.comment = r.comment();
        e.createdAt = r.createdAt();
        return e;
    }
}
