package pe.logyx.notification.infrastructure.scheduling;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.notification.application.EmailQueueService;

import java.time.OffsetDateTime;

/** V24 (2.2): envía los correos de aviso diferidos por silencio o resumen diario. Sin usuario: SystemTx en el servicio. */
@ApplicationScoped
public class EmailQueueScheduler {

    @Inject EmailQueueService queue;

    @Scheduled(every = "{logyx.notifications.email-queue.every}", identity = "notification-email-queue",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void flush() {
        queue.flushDue(OffsetDateTime.now());
    }
}
