package pe.logyx.reputation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.documents.application.DocumentQueries;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.reputation.application.event.ReviewSubmitted;
import pe.logyx.reputation.application.port.out.ReviewRepository;
import pe.logyx.reputation.domain.ReviewEligibilityPolicy;
import pe.logyx.reputation.domain.model.Review;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.domain.model.Shipment;

import java.util.UUID;

/** Calificación post-entrega (RF40/RF41) con RN06; el Trust Score se recalcula tras el commit (RN09). */
@ApplicationScoped
public class ReviewService {

    @Inject ReviewRepository reviews;
    @Inject ShipmentQueries shipments;
    @Inject DocumentQueries documents;
    @Inject OrganizationDirectory organizations;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<ReviewSubmitted> submitted;

    @Transactional
    public ReviewSummary submit(UUID shipmentId, int punctuality, int cargoCondition, int communication,
                                String comment) {
        Shipment s = shipments.detail(shipmentId).shipment();
        UUID me = tenant.orgId();
        if (!s.isParty(me)) throw BusinessException.notFound("Envío");
        ReviewEligibilityPolicy.ensureCanReview(s.status() == Shipment.Status.DELIVERED,                    // RN06
                documents.hasDeliveryPhoto(s.id()), reviews.exists(s.id(), me));
        Review r = Review.write(s.id(), s.requesterOrgId(), s.carrierOrgId(), me, tenant.profileId(), punctuality,
                cargoCondition, communication, comment);
        reviews.insert(r);
        audit.record(tenant.profileId(), me, "REVIEW_SUBMITTED", "review", r.id(),
                "envio=" + s.id() + " rating=" + r.rating());
        String reviewer = organizations.get(me).legalName();
        submitted.fire(new ReviewSubmitted(r.id(), s.id(), me, reviewer, r.reviewedOrgId(), r.rating()));
        return new ReviewSummary(r, reviewer);
    }
}
