package pe.logyx.reputation.application;

import pe.logyx.reputation.domain.model.Review;

/** Calificación con el nombre de la organización que calificó. */
public record ReviewSummary(Review review, String reviewerName) {}
