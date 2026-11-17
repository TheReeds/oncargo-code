package pe.logyx.routing.infrastructure.web;

import pe.logyx.routing.domain.PolylineCodec;
import pe.logyx.routing.domain.model.LatLng;
import pe.logyx.routing.domain.model.RoadLeg;
import pe.logyx.routing.domain.model.RoutePlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** DTOs de {@code GET /api/v1/routes} (ADR-0008). Los nombres simples son schemas del OpenAPI. */
public final class RoutingDtos {

    /** Precisión de la polyline publicada (la que decodifica {@code web/src/lib/routing}). */
    public static final int POLYLINE_PRECISION = 5;

    private RoutingDtos() {}

    /** Tramo entre dos paradas consecutivas. {@code estimated}: sin dato vial, línea recta × 1.35. */
    public record RouteLegView(String fromCity, String toCity, BigDecimal distanceKm, BigDecimal durationMin,
                               boolean estimated) {
        static RouteLegView of(RoadLeg l) {
            return new RouteLegView(l.from().name(), l.to().name(), round(l.distanceKm()), round(l.durationMin()),
                    l.estimated());
        }
    }

    /**
     * Ruta por carretera por las paradas en orden. {@code polyline}: geometría codificada (algoritmo de Google,
     * precisión {@code polylinePrecision}); {@code attribution}: atribución obligatoria de OpenStreetMap (ODbL).
     * {@code durationMin} es la del perfil de auto (orientativa para camión).
     */
    public record RouteView(List<String> stops, BigDecimal distanceKm, BigDecimal durationMin, boolean estimated,
                            List<RouteLegView> legs, String polyline, int polylinePrecision, String attribution) {
        static RouteView of(RoutePlan plan, List<LatLng> geometry, String attribution) {
            return new RouteView(plan.stops().stream().map(Enum::name).toList(), round(plan.distanceKm()),
                    round(plan.durationMin()), plan.estimated(), plan.legs().stream().map(RouteLegView::of).toList(),
                    PolylineCodec.encode(geometry, POLYLINE_PRECISION), POLYLINE_PRECISION, attribution);
        }
    }

    static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }
}
