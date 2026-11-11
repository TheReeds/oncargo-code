package pe.logyx.dashboard.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import pe.logyx.dashboard.application.DashboardSummary;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.trip.application.ReturnLoadMatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
            long deliveredThisMonth,
            /** CARRIER: viajes publicados abiertos (OPEN/FILLING). */
            Long openTrips,
            /** CARRIER: reservas de sus viajes por confirmar. */
            Long pendingTripBookings,
            /** CARRIER: sugerencias Smart Load pendientes y vigentes. */
            Long pendingSmartLoads,
            /** CARRIER: cargas de retorno disponibles (RF61–RF62). */
            Long returnLoads,
            /** CARRIER: las 3 mejores cargas de retorno; el listado completo está en GET /return-loads. */
            List<ReturnLoadHintView> topReturnLoads,
            /** PYME: reservas en viajes publicados pendientes o confirmadas. */
            Long activeTripBookings) {

        static DashboardSummaryView of(DashboardSummary s) {
            return new DashboardSummaryView(s.role(), s.activeRequests(), s.bidsToReview(), s.shipmentsInTransit(),
                    s.compatibleLoads(), s.pendingBids(), s.activeShipments(), s.deliveredThisMonth(),
                    s.openTrips(), s.pendingTripBookings(), s.pendingSmartLoads(), s.returnLoads(),
                    s.topReturnLoads() == null ? null : s.topReturnLoads().stream().map(ReturnLoadHintView::of).toList(),
                    s.activeTripBookings());
        }
    }

    /** Resumen de una carga de retorno para el dashboard (detalle completo en {@code GET /return-loads}). */
    public record ReturnLoadHintView(UUID requestId, City originCity, City destinationCity, BigDecimal weightKg,
                                     LocalDate pickupDate, BigDecimal suggestedPrice, double distanceKm, int matchScore) {

        static ReturnLoadHintView of(ReturnLoadMatch m) {
            var c = m.request().request().cargo();
            return new ReturnLoadHintView(m.request().request().id(), c.originCity(), c.destinationCity(),
                    c.weightKg(), c.pickupDate(), m.request().request().pricing().suggestedPrice(),
                    m.match().distanceKm(), m.match().score());
        }
    }
}
