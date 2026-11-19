package pe.logyx.notification.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheNotificationRepository implements NotificationRepository {

    @Override
    public Optional<Notification> findOwned(UUID id, UUID profileId) {
        return NotificationEntity.<NotificationEntity>find("id = ?1 and profileId = ?2", id, profileId)
                .firstResultOptional().map(NotificationEntity::toDomain);
    }

    @Override
    public Paged<Notification> listByProfile(UUID profileId, boolean unreadOnly, boolean mineOnly, PageRequest page) {
        String where = "profileId = ?1" + (unreadOnly ? " and readAt is null" : "") + (mineOnly ? " and mine = true" : "");
        var q = NotificationEntity.<NotificationEntity>find(where + " order by createdAt desc, id", profileId)
                .page(page.page(), page.size());
        return new Paged<>(q.list().stream().map(NotificationEntity::toDomain).toList(), page,
                NotificationEntity.count(where, profileId));
    }

    @Override
    public long countUnread(UUID profileId) {
        return NotificationEntity.count("profileId = ?1 and readAt is null", profileId);
    }

    @Override
    public boolean markRead(UUID id, UUID profileId, OffsetDateTime now) {
        // Solo read_at: logyx_app tiene UPDATE únicamente sobre esa columna (V16).
        return NotificationEntity.update("readAt = ?1 where id = ?2 and profileId = ?3 and readAt is null",
                now, id, profileId) > 0;
    }

    @Override
    public int markAllRead(UUID profileId, OffsetDateTime now) {
        return NotificationEntity.update("readAt = ?1 where profileId = ?2 and readAt is null", now, profileId);
    }

    @Override
    public void insert(Notification n) {
        Objects.requireNonNull(n.profileId(), "Un aviso guardado siempre es de una persona (V16)");
        NotificationEntity.of(n).persist();
    }
}
