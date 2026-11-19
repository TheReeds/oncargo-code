package pe.logyx.alerts.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Bitácora {@code org_route_alerts} (V24) de las alertas implícitas: idempotencia y tope por hora. Solo sistema. */
public interface RouteAlertLog {
    long sentSince(UUID organizationId, OffsetDateTime since);
    /** false si ya se registró (organización, solicitud): reintento del evento. */
    boolean record(UUID organizationId, UUID requestId);
}
