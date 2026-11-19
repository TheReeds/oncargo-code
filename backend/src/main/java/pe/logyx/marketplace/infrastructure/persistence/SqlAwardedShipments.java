package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.marketplace.application.port.out.AwardedShipments;
import pe.logyx.marketplace.domain.BackupAwardPolicy;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Lee el último envío de la solicitud con SQL nativo y bajo la RLS de quien llama (la PYME dueña ve sus envíos):
 * evita que marketplace dependa del repositorio de shipment solo para dos fechas.
 */
@ApplicationScoped
public class SqlAwardedShipments implements AwardedShipments {

    @Inject EntityManager em;

    @Override
    public Optional<BackupAwardPolicy.AwardedShipment> latest(UUID requestId) {
        var rows = em.createNativeQuery("SELECT created_at, cancelled_at FROM shipments WHERE request_id = ?1 "
                        + "ORDER BY created_at DESC LIMIT 1").setParameter(1, requestId).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] r = (Object[]) rows.getFirst();
        return Optional.of(new BackupAwardPolicy.AwardedShipment(odt(r[0]), odt(r[1])));
    }

    private static OffsetDateTime odt(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime d) return d;
        if (o instanceof java.time.Instant i) return i.atOffset(java.time.ZoneOffset.UTC);
        if (o instanceof java.sql.Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Tipo de fecha inesperado: " + o.getClass());
    }
}
