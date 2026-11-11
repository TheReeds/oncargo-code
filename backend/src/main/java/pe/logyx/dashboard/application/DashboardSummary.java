package pe.logyx.dashboard.application;

import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.trip.application.ReturnLoadMatch;

import java.util.List;

/**
 * KPIs del usuario actual. Los campos que no aplican al rol son {@code null}.
 * PYME: activeRequests, bidsToReview, shipmentsInTransit, deliveredThisMonth, activeTripBookings.
 * CARRIER: compatibleLoads, pendingBids, activeShipments, deliveredThisMonth, openTrips, pendingTripBookings,
 * pendingSmartLoads, returnLoads, topReturnLoads.
 */
public record DashboardSummary(OrgType role,
                               Long activeRequests, Long bidsToReview, Long shipmentsInTransit,
                               Long compatibleLoads, Long pendingBids, Long activeShipments,
                               long deliveredThisMonth,
                               Long openTrips, Long pendingTripBookings, Long pendingSmartLoads, Long returnLoads,
                               List<ReturnLoadMatch> topReturnLoads, Long activeTripBookings) {}
