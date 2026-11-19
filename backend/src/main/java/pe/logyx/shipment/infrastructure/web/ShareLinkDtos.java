package pe.logyx.shipment.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.PolylineCodec;
import pe.logyx.shipment.application.PublicTracking;
import pe.logyx.shipment.application.ShareLinkService.ShareLinkSummary;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Enlaces públicos de seguimiento (roadmap 1.6, ADR-0011). */
public final class ShareLinkDtos {
    private ShareLinkDtos() {}

    /** Precisión de la polyline publicada (la misma que {@code GET /routes}; la decodifica {@code web/src/lib/routing}). */
    static final int POLYLINE_PRECISION = 5;

    /** {@code expiresInDays}: caducidad propia de 1 a 30 días (opcional). Siempre vence 7 días después de la entrega. */
    public record CreateShareLink(@Min(value = 1, message = "La caducidad mínima es 1 día")
                                  @Max(value = 30, message = "La caducidad máxima es 30 días") Integer expiresInDays) {}

    /**
     * Un enlace, sin el token (solo se ve al crearlo). {@code tokenHint}: últimos 4 caracteres, para reconocerlo.
     * {@code expiresAt}: caducidad efectiva (la elegida o la entrega + 7 días; null = mientras el envío siga abierto).
     */
    public record ShareLinkView(UUID id, String tokenHint,
                                @Schema(enumeration = {"ACTIVE", "EXPIRED", "REVOKED"}) String status,
                                OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime revokedAt,
                                OffsetDateTime lastViewedAt, int views) {
        static ShareLinkView of(ShareLinkSummary s) {
            var l = s.link();
            return new ShareLinkView(l.id(), l.tokenHint(), s.status().name(), l.createdAt(), s.effectiveExpiresAt(),
                    l.revokedAt(), l.lastViewedAt(), l.views());
        }
    }

    /** Respuesta de crear: {@code token} y {@code url} se muestran <b>una sola vez</b> (en la BD solo queda el hash). */
    public record ShareLinkCreated(ShareLinkView link, String token, String url) {}

    /** Hito público de una parada (sin dirección ni contacto). */
    public record PublicTrackingStop(int sequence, ShipmentStop.Type type, City city, ShipmentStop.Status status,
                                     OffsetDateTime arrivedAt, OffsetDateTime completedAt) {}

    /** Ruta por carretera (ADR-0008): polyline codificada y atribución de OpenStreetMap. */
    public record PublicTrackingRoute(String polyline, int polylinePrecision, double distanceKm, double durationMin,
                                      String attribution) {}

    /**
     * Seguimiento público: <b>lista blanca</b>. Código del envío, estado, ciudades, fecha de recojo, hitos con hora, ETA
     * aproximada (ventana) y ruta. Nunca precio, contactos, direcciones, placa, conductor ni datos o ids de las
     * organizaciones o del envío.
     */
    public record PublicTrackingView(String code, Shipment.Status status, City originCity, City destinationCity,
                                     LocalDate pickupDate, OffsetDateTime deliveredAt, List<PublicTrackingStop> stops,
                                     OffsetDateTime etaFrom, OffsetDateTime etaTo, PublicTrackingRoute route,
                                     OffsetDateTime updatedAt, OffsetDateTime linkExpiresAt) {
        static PublicTrackingView of(PublicTracking t) {
            PublicTrackingRoute route = t.route().isEmpty() ? null : new PublicTrackingRoute(
                    PolylineCodec.encode(t.route(), POLYLINE_PRECISION), POLYLINE_PRECISION,
                    Math.round(t.distanceKm() * 10) / 10.0, Math.round(t.durationMin()), t.routeAttribution());
            return new PublicTrackingView(t.code(), t.status(), t.originCity(), t.destinationCity(), t.pickupDate(),
                    t.deliveredAt(), t.stops().stream().map(s -> new PublicTrackingStop(s.sequence(), s.type(), s.city(),
                    s.status(), s.arrivedAt(), s.completedAt())).toList(), t.etaFrom(), t.etaTo(), route,
                    t.updatedAt(), t.linkExpiresAt());
        }
    }
}
