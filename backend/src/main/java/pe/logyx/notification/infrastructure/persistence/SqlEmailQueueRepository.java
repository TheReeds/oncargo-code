package pe.logyx.notification.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.notification.application.port.out.EmailQueueRepository;
import pe.logyx.notification.domain.model.NotificationCategory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** {@code notification_email_queue} (V24). Solo dentro de SystemTx: {@code logyx_app} no tiene ningún privilegio. */
@ApplicationScoped
public class SqlEmailQueueRepository implements EmailQueueRepository {

    @Inject EntityManager em;

    @Override
    public void enqueue(QueuedEmail q) {
        em.createNativeQuery("INSERT INTO notification_email_queue (id, profile_id, org_id, category, title, body, reason, "
                        + "send_after) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
                .setParameter(1, q.id()).setParameter(2, q.profileId()).setParameter(3, q.orgId())
                .setParameter(4, q.category().name()).setParameter(5, q.title()).setParameter(6, q.body())
                .setParameter(7, q.reason()).setParameter(8, q.sendAfter()).executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QueuedEmail> lockDue(OffsetDateTime now, int limit) {
        List<Object[]> rows = em.createNativeQuery("SELECT id, profile_id, org_id, category, title, body, reason, send_after, "
                        + "created_at FROM notification_email_queue WHERE sent_at IS NULL AND send_after <= ?1 "
                        + "ORDER BY profile_id, created_at LIMIT ?2 FOR UPDATE SKIP LOCKED")
                .setParameter(1, now).setParameter(2, limit).getResultList();
        List<QueuedEmail> out = new ArrayList<>();
        for (Object[] r : rows) {
            NotificationCategory c;
            try {
                c = NotificationCategory.valueOf((String) r[3]);
            } catch (IllegalArgumentException e) {
                c = NotificationCategory.ACCOUNT;                      // categoría retirada: se envía como de cuenta
            }
            out.add(new QueuedEmail((UUID) r[0], (UUID) r[1], (UUID) r[2], c, (String) r[4], (String) r[5],
                    (String) r[6], SqlEmailSettingsRepository.offset(r[7]), SqlEmailSettingsRepository.offset(r[8])));
        }
        return out;
    }

    @Override
    public void markSent(Collection<UUID> ids, OffsetDateTime at) {
        if (ids.isEmpty()) return;
        String array = ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
        em.createNativeQuery("UPDATE notification_email_queue SET sent_at = ?1 WHERE id = ANY(CAST(?2 AS uuid[]))")
                .setParameter(1, at).setParameter(2, array).executeUpdate();
    }
}
