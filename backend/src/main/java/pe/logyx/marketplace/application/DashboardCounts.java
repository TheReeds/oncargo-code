package pe.logyx.marketplace.application;

/** Contadores del marketplace para los KPIs del dashboard (según el rol del usuario actual). */
public record DashboardCounts(long activeRequests, long bidsToReview, long compatibleLoads, long pendingBids) {}
