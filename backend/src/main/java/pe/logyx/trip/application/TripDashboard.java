package pe.logyx.trip.application;

import java.util.List;

/**
 * Extras del dashboard. Transportista: viajes abiertos, reservas por confirmar, sugerencias Smart Load pendientes y
 * cargas de retorno. PYME: reservas activas ({@code activeBookings}); el resto es null.
 */
public record TripDashboard(Long openTrips, Long pendingBookings, Long pendingSmartLoads, Long returnLoads,
                            List<ReturnLoadMatch> topReturnLoads, Long activeBookings) {}
