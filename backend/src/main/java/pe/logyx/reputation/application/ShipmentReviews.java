package pe.logyx.reputation.application;

import pe.logyx.reputation.domain.ReviewEligibilityPolicy;

import java.util.List;

/** Calificaciones de un envío y si el usuario actual puede calificarlo ahora (RF45); {@code blocker} explica por qué no. */
public record ShipmentReviews(boolean canReview, ReviewEligibilityPolicy.Blocker blocker, List<ReviewSummary> reviews) {}
