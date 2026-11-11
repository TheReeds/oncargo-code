package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import pe.logyx.notification.application.event.NotificationsPublished;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.tenancy.SystemTx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inserta notificaciones. SystemTx justificado (ADR-0004): casi siempre el destinatario es OTRA organización que
 * la que actúa, y logyx_app no tiene política de INSERT sobre notifications (V8). Se invoca desde los observadores
 * {@code @Outboxed}: se une a la transacción de sistema del despacho del outbox, así que las notificaciones y la
 * marca {@code DONE} del evento se confirman juntas. Un fallo se propaga para que el outbox reintente.
 *
 * <p>Luego dispara {@link NotificationsPublished} (síncrono, misma transacción) con el contador de no leídas de cada
 * organización: el tiempo real (ADR-0007) lo proyecta como {@code notification.created}.
 */
@ApplicationScoped
public class NotificationPublisher {

    @Inject NotificationRepository notifications;
    @Inject SystemTx system;
    @Inject Event<NotificationsPublished> published;

    public void publish(List<Notification> list) {
        if (list.isEmpty()) return;
        system.run(() -> {
            list.forEach(notifications::save);
            Map<UUID, Long> unread = new HashMap<>();
            List<NotificationsPublished.Item> items = list.stream().map(n -> new NotificationsPublished.Item(n.id(),
                    n.orgId(), n.type().name(), n.relatedType() == null ? null : n.relatedType().name(), n.relatedId(),
                    unread.computeIfAbsent(n.orgId(), notifications::countUnread))).toList();
            published.fire(new NotificationsPublished(items));
        });
    }
}
