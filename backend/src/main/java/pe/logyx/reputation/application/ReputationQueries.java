package pe.logyx.reputation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.documents.application.DocumentQueries;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.reputation.application.port.out.ReputationScoreRepository;
import pe.logyx.reputation.application.port.out.ReviewRepository;
import pe.logyx.reputation.domain.ReviewEligibilityPolicy;
import pe.logyx.reputation.domain.model.ReputationScore;
import pe.logyx.reputation.domain.model.Review;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.domain.model.Shipment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reputación pública (RLS: reviews y reputation_scores se leen entre organizaciones, RF44). */
@ApplicationScoped
@Transactional
public class ReputationQueries {

    @Inject ReviewRepository reviews;
    @Inject ReputationScoreRepository scores;
    @Inject OrganizationDirectory organizations;
    @Inject ShipmentQueries shipments;
    @Inject DocumentQueries documents;
    @Inject ReputationSettings settings;
    @Inject TenantContext tenant;

    /** Trust Score por organización; sin calificaciones = prior (75). Lo usa marketplace en ofertas y solicitudes. */
    public Map<UUID, BigDecimal> trustScores(Collection<UUID> orgIds) {
        if (orgIds.isEmpty()) return Map.of();
        Map<UUID, BigDecimal> out = new HashMap<>();
        orgIds.forEach(id -> out.put(id, prior()));
        scores.findAll(orgIds).forEach(s -> out.put(s.orgId(), s.trustScore()));
        return out;
    }

    public ReputationProfile profile(UUID orgId) {
        Organization o = organizations.get(orgId);
        ReputationScore s = scores.find(orgId).orElse(ReputationScore.initial(orgId, prior()));
        List<ReviewSummary> recent = withNames(reviews.pageReceived(orgId, new PageRequest(0, 5)).items());
        return new ReputationProfile(o, s, settings.priorMean(), settings.priorWeight(), recent);
    }

    public Paged<ReviewSummary> received(UUID orgId, PageRequest page) {
        organizations.get(orgId);   // 404 si no existe
        Paged<Review> p = reviews.pageReceived(orgId, page);
        return new Paged<>(withNames(p.items()), page, p.total());
    }

    /** Calificaciones del envío + elegibilidad del usuario actual (RN06). */
    public ShipmentReviews forShipment(UUID shipmentId) {
        Shipment s = shipments.detail(shipmentId).shipment();
        List<ReviewSummary> list = withNames(reviews.listByShipment(s.id()));
        boolean party = tenant.orgType() != OrgType.OPERATOR && !tenant.isDriver() && s.isParty(tenant.orgId());
        if (!party) return new ShipmentReviews(false, null, list);
        UUID me = tenant.orgId();
        var blocker = ReviewEligibilityPolicy.blocker(s.status() == Shipment.Status.DELIVERED,
                documents.hasDeliveryPhoto(s.id()), list.stream().anyMatch(r -> r.review().reviewerOrgId().equals(me)));
        return new ShipmentReviews(blocker.isEmpty(), blocker.orElse(null), list);
    }

    private BigDecimal prior() {
        return settings.priorMean().setScale(1, RoundingMode.HALF_UP);
    }

    private List<ReviewSummary> withNames(List<Review> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, Organization> orgs = organizations.byIds(list.stream().map(Review::reviewerOrgId)
                .collect(Collectors.toSet()));
        return list.stream().map(r -> new ReviewSummary(r,
                Optional.ofNullable(orgs.get(r.reviewerOrgId())).map(Organization::legalName).orElse(null))).toList();
    }
}
