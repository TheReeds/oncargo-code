package pe.logyx.alerts.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.alerts.application.port.out.RouteAlertLog;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code org_route_alerts} (V24). Solo dentro del despacho del outbox (SystemTx): logyx_app no tiene acceso. */
@ApplicationScoped
public class SqlRouteAlertLog implements RouteAlertLog {

    @Inject EntityManager em;

    @Override
    public long sentSince(UUID organizationId, OffsetDateTime since) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM org_route_alerts WHERE organization_id = ?1 "
                + "AND created_at >= ?2").setParameter(1, organizationId).setParameter(2, since).getSingleResult()).longValue();
    }

    @Override
    public boolean record(UUID organizationId, UUID requestId) {
        return em.createNativeQuery("INSERT INTO org_route_alerts (organization_id, request_id) VALUES (?1, ?2) "
                + "ON CONFLICT DO NOTHING").setParameter(1, organizationId).setParameter(2, requestId).executeUpdate() > 0;
    }
}
