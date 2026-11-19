package pe.logyx.notification.application.port.out;

import pe.logyx.notification.domain.model.NotificationCategory;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Correos de aviso diferidos (V24, {@code notification_email_queue}): por horario de silencio ({@code QUIET}) o resumen
 * diario ({@code DIGEST}). Solo sistema: {@code logyx_app} no la ve. El destinatario se resuelve al enviar.
 */
public interface EmailQueueRepository {

    record QueuedEmail(UUID id, UUID profileId, UUID orgId, NotificationCategory category, String title, String body,
                       String reason, OffsetDateTime sendAfter, OffsetDateTime createdAt) {}

    void enqueue(QueuedEmail email);

    /** Vencidos y sin enviar, bloqueados con {@code FOR UPDATE SKIP LOCKED} (otra instancia toma los demás). */
    List<QueuedEmail> lockDue(OffsetDateTime now, int limit);

    void markSent(Collection<UUID> ids, OffsetDateTime at);
}
