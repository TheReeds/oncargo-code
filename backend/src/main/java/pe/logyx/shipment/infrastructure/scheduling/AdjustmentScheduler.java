package pe.logyx.shipment.infrastructure.scheduling;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.shipment.application.AdjustmentService;

/** 2.6: escala al operador los ajustes que la PYME no respondió a tiempo. Sin usuario: SystemTx dentro del servicio. */
@ApplicationScoped
public class AdjustmentScheduler {

    @Inject AdjustmentService adjustments;

    @Scheduled(every = "{logyx.adjustments.every}", identity = "adjustment-escalation",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void escalate() {
        adjustments.escalateDue();
    }
}
