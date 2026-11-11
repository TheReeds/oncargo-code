package pe.logyx.notification.application.event;

import java.util.List;
import java.util.UUID;

/**
 * Se crearon notificaciones (dentro de la transacción del despacho del outbox). <b>No</b> es un {@code OutboxEvent}:
 * se dispara de forma síncrona en esa misma transacción para que el tiempo real (ADR-0007) proyecte
 * {@code notification.created} con el contador de no leídas ya actualizado, atómico con las notificaciones.
 */
public record NotificationsPublished(List<Item> items) {
    public record Item(UUID notificationId, UUID orgId, String type, String relatedType, UUID relatedId,
                       long unreadCount) {}
}
