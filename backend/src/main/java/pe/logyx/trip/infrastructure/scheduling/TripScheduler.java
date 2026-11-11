package pe.logyx.trip.infrastructure.scheduling;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.trip.application.SmartLoadService;
import pe.logyx.trip.application.TripMaintenanceUseCase;

/**
 * Jobs programados sin usuario (corren como dueño del esquema, sin RLS). Los intervalos se configuran con
 * {@code logyx.smartload.every} y {@code logyx.trip.maintenance.every}; el valor {@code off} los desactiva (tests).
 */
@ApplicationScoped
public class TripScheduler {

    @Inject SmartLoadService smartLoads;
    @Inject TripMaintenanceUseCase maintenance;

    @Scheduled(every = "{logyx.smartload.every}", identity = "smart-load-planner",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planSmartLoads() {
        smartLoads.generateForAllCarriers();
    }

    @Scheduled(every = "{logyx.trip.maintenance.every}", identity = "trip-maintenance",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void maintain() {
        maintenance.run();
    }
}
