package pe.logyx.dashboard.application;

import pe.logyx.shared.tenancy.OrgType;

/**
 * KPIs del usuario actual. Los campos que no aplican al rol son {@code null}.
 * PYME: activeRequests, bidsToReview, shipmentsInTransit, deliveredThisMonth.
 * CARRIER: compatibleLoads, pendingBids, activeShipments, deliveredThisMonth.
 */
public record DashboardSummary(OrgType role,
                               Long activeRequests, Long bidsToReview, Long shipmentsInTransit,
                               Long compatibleLoads, Long pendingBids, Long activeShipments,
                               long deliveredThisMonth) {}
