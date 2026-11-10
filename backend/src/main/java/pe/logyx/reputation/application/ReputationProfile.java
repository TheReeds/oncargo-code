package pe.logyx.reputation.application;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.reputation.domain.model.ReputationScore;

import java.math.BigDecimal;
import java.util.List;

/** Perfil público de reputación de una organización (RF44). */
public record ReputationProfile(Organization organization, ReputationScore score, BigDecimal priorMean,
                                int priorWeight, List<ReviewSummary> recentReviews) {}
