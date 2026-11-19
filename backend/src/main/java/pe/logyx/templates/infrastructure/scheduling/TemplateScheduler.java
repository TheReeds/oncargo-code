package pe.logyx.templates.infrastructure.scheduling;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.templates.application.ScheduledTemplates;

import java.time.OffsetDateTime;

/** Publica las cargas recurrentes que ya tocan (roadmap 2.1). Sin usuario: cada una corre bajo su organización. */
@ApplicationScoped
public class TemplateScheduler {

    @Inject ScheduledTemplates templates;

    @Scheduled(every = "{logyx.templates.every}", identity = "template-scheduler",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void run() {
        templates.runDue(OffsetDateTime.now());
    }
}
