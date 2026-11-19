package pe.logyx.notification.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.notification.application.port.out.ResourceAuthors;
import pe.logyx.notification.domain.model.NotificationTarget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Autor del recurso de un aviso dentro de una organización (V16, «Tuyo»), con SQL nativo de solo lectura sobre las
 * columnas de autoría ({@code shipment_requests.created_by}, {@code bids.created_by}). Corre en el despacho del outbox
 * (SystemTx, sin RLS), como {@code realtime.SqlShipmentDrivers}. Para la PYME el autor es quien publicó la solicitud;
 * para el transportista, quien ofertó.
 */
@ApplicationScoped
public class SqlResourceAuthors implements ResourceAuthors {

    @Inject EntityManager em;

    /** Autor según el lado: requester → la solicitud; carrier → su oferta. :org y :id. */
    private static final String BY_SHIPMENT = """
            SELECT CASE WHEN s.requester_org_id = :org THEN r.created_by WHEN s.carrier_org_id = :org THEN b.created_by END
            FROM shipments s JOIN shipment_requests r ON r.id = s.request_id JOIN bids b ON b.id = s.bid_id
            WHERE s.id = CAST(:id AS uuid)""";

    @Override
    public Optional<UUID> authorIn(UUID orgId, NotificationTarget type, UUID id) {
        String sql = switch (type) {
            case REQUEST -> """
                    SELECT CASE WHEN r.requester_org_id = :org THEN r.created_by
                                ELSE (SELECT b.created_by FROM bids b WHERE b.request_id = r.id AND b.carrier_org_id = :org
                                      ORDER BY b.created_at DESC LIMIT 1) END
                    FROM shipment_requests r WHERE r.id = CAST(:id AS uuid)""";
            case BID -> """
                    SELECT CASE WHEN b.carrier_org_id = :org THEN b.created_by WHEN b.requester_org_id = :org THEN r.created_by END
                    FROM bids b JOIN shipment_requests r ON r.id = b.request_id WHERE b.id = CAST(:id AS uuid)""";
            case COUNTER_OFFER -> """
                    SELECT CASE WHEN b.carrier_org_id = :org THEN b.created_by WHEN b.requester_org_id = :org THEN r.created_by END
                    FROM counter_offers c JOIN bids b ON b.id = c.bid_id JOIN shipment_requests r ON r.id = b.request_id
                    WHERE c.id = CAST(:id AS uuid)""";
            case SHIPMENT -> BY_SHIPMENT;
            case INCIDENT -> BY_SHIPMENT.replace("WHERE s.id = CAST(:id AS uuid)",
                    "WHERE s.id = (SELECT i.shipment_id FROM incidents i WHERE i.id = CAST(:id AS uuid))");
            default -> null;
        };
        if (sql == null) return Optional.empty();
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(sql).setParameter("org", orgId).setParameter("id", id.toString())
                .getResultList();
        return rows.stream().filter(UUID.class::isInstance).map(UUID.class::cast).findFirst();
    }
}
