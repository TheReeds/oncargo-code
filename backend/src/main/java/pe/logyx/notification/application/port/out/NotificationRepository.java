package pe.logyx.notification.application.port.out;

import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Avisos por persona (V16). Bajo RLS el usuario solo ve los suyos; los filtros explícitos son defensa en profundidad. */
public interface NotificationRepository {
    Optional<Notification> findOwned(UUID id, UUID profileId);
    /** Más recientes primero. {@code mineOnly} = solo los de algo que la persona creó. */
    Paged<Notification> listByProfile(UUID profileId, boolean unreadOnly, boolean mineOnly, PageRequest page);
    long countUnread(UUID profileId);
    /** Marca como leído solo {@code read_at} (logyx_app no puede actualizar otra columna). */
    boolean markRead(UUID id, UUID profileId, OffsetDateTime now);
    int markAllRead(UUID profileId, OffsetDateTime now);
    /** Inserta (solo en contexto de sistema, ver NotificationPublisher). */
    void insert(Notification notification);
}
