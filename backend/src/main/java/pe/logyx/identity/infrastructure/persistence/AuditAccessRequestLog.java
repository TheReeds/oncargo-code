package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.identity.application.port.out.AccessRequestLog;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cuenta los {@code ACCESS_REQUESTED} de una persona en {@code audit_log} (RLS {@code audit_own_read}). */
@ApplicationScoped
public class AuditAccessRequestLog implements AccessRequestLog {

    public static final String ACTION = "ACCESS_REQUESTED";

    @Inject EntityManager em;

    @Override
    public long countSince(UUID profileId, OffsetDateTime since) {
        Object v = em.createNativeQuery("SELECT count(*) FROM audit_log WHERE action = ?1 AND actor_profile_id = ?2 "
                        + "AND occurred_at >= ?3")
                .setParameter(1, ACTION).setParameter(2, profileId).setParameter(3, since).getSingleResult();
        return ((Number) v).longValue();
    }
}
