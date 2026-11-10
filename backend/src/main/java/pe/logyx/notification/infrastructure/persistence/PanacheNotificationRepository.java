package pe.logyx.notification.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheNotificationRepository implements NotificationRepository {

    @Override
    public Optional<Notification> findOwned(UUID id, UUID orgId) {
        return NotificationEntity.<NotificationEntity>find("id = ?1 and orgId = ?2", id, orgId)
                .firstResultOptional().map(NotificationEntity::toDomain);
    }

    @Override
    public Paged<Notification> listByOrg(UUID orgId, boolean unreadOnly, PageRequest page) {
        String where = "orgId = ?1" + (unreadOnly ? " and readAt is null" : "");
        var q = NotificationEntity.<NotificationEntity>find(where + " order by createdAt desc, id", orgId)
                .page(page.page(), page.size());
        return new Paged<>(q.list().stream().map(NotificationEntity::toDomain).toList(), page,
                NotificationEntity.count(where, orgId));
    }

    @Override
    public long countUnread(UUID orgId) {
        return NotificationEntity.count("orgId = ?1 and readAt is null", orgId);
    }

    @Override
    public int markAllRead(UUID orgId, OffsetDateTime now) {
        return NotificationEntity.update("readAt = ?1 where orgId = ?2 and readAt is null", now, orgId);
    }

    @Override
    public void save(Notification n) {
        NotificationEntity e = NotificationEntity.findById(n.id());
        if (e == null) {
            e = new NotificationEntity();
            e.apply(n);
            e.persist();
        } else {
            e.apply(n);
        }
    }
}
