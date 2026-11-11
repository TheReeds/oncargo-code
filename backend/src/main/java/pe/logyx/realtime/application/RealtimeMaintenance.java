package pe.logyx.realtime.application;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.realtime.application.port.out.RealtimeEventRepository;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Borra la proyección vieja ({@code logyx.realtime.retention}). SystemTx: job sin usuario (logyx_app no borra). */
@ApplicationScoped
public class RealtimeMaintenance {

    @Inject RealtimeEventRepository events;
    @Inject SystemTx system;

    @ConfigProperty(name = "logyx.realtime.retention", defaultValue = "PT24H")
    Duration retention;

    @Scheduled(every = "{logyx.realtime.cleanup-every}", identity = "realtime-cleanup", delayed = "1m",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void cleanup() {
        system.call(() -> events.deleteOlderThan(OffsetDateTime.now().minus(retention)));
    }
}
