package pe.logyx.dashboard.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import pe.logyx.dashboard.application.DashboardSummary;
import pe.logyx.shared.tenancy.OrgType;

public final class DashboardDtos {
    private DashboardDtos() {}

    /** KPIs por rol; los campos de otro rol se omiten del JSON. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardSummaryView(
            OrgType role,
            /** PYME: solicitudes publicadas con subasta vigente. */
            Long activeRequests,
            /** PYME: ofertas pendientes sobre solicitudes publicadas. */
            Long bidsToReview,
            /** PYME: envíos en tránsito. */
            Long shipmentsInTransit,
            /** CARRIER: cargas abiertas que caben en su vehículo disponible más grande. */
            Long compatibleLoads,
            /** CARRIER: sus ofertas aún pendientes. */
            Long pendingBids,
            /** CARRIER: envíos pendientes o en tránsito. */
            Long activeShipments,
            /** Ambos: envíos entregados desde el día 1 del mes (hora de Lima). */
            long deliveredThisMonth) {

        static DashboardSummaryView of(DashboardSummary s) {
            return new DashboardSummaryView(s.role(), s.activeRequests(), s.bidsToReview(), s.shipmentsInTransit(),
                    s.compatibleLoads(), s.pendingBids(), s.activeShipments(), s.deliveredThisMonth());
        }
    }
}
