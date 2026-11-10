package pe.logyx.notification.application.port.out;

import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Optional<Notification> findOwned(UUID id, UUID orgId);
    /** Más recientes primero. */
    Paged<Notification> listByOrg(UUID orgId, boolean unreadOnly, PageRequest page);
    long countUnread(UUID orgId);
    int markAllRead(UUID orgId, OffsetDateTime now);
    /** Inserta o actualiza. La inserción solo ocurre en contexto de sistema (ver NotificationPublisher). */
    void save(Notification notification);
}
