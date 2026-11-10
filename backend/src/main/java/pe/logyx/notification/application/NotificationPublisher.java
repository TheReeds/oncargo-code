package pe.logyx.notification.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.tenancy.SystemTx;

import java.util.List;

/**
 * Inserta notificaciones. SystemTx justificado (ADR-0004): casi siempre el destinatario es OTRA organización que
 * la que actúa, y logyx_app no tiene política de INSERT sobre notifications (V8). Se invoca desde observadores
 * AFTER_SUCCESS: corre en una transacción nueva, solo si el caso de uso confirmó, y un fallo aquí no revierte la
 * operación de negocio (se registra y se pierde la notificación).
 */
@ApplicationScoped
public class NotificationPublisher {

    @Inject NotificationRepository notifications;
    @Inject SystemTx system;

    public void publish(List<Notification> list) {
        if (list.isEmpty()) return;
        try {
            system.run(() -> list.forEach(notifications::save));
        } catch (RuntimeException e) {
            Log.errorf(e, "No se pudieron registrar %d notificaciones", list.size());
        }
    }
}
