package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.tenancy.SystemTx;

import java.util.List;

/**
 * Inserta notificaciones. SystemTx justificado (ADR-0004): casi siempre el destinatario es OTRA organización que
 * la que actúa, y logyx_app no tiene política de INSERT sobre notifications (V8). Se invoca desde los observadores
 * {@code @Outboxed}: se une a la transacción de sistema del despacho del outbox, así que las notificaciones y la
 * marca {@code DONE} del evento se confirman juntas. Un fallo se propaga para que el outbox reintente.
 */
@ApplicationScoped
public class NotificationPublisher {

    @Inject NotificationRepository notifications;
    @Inject SystemTx system;

    public void publish(List<Notification> list) {
        if (list.isEmpty()) return;
        system.run(() -> list.forEach(notifications::save));
    }
}
