package pe.logyx.settlement.infrastructure.scheduling;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.settlement.application.SettlementReminders;
import pe.logyx.settlement.application.SettlementService;

/** Recordatorios de pago (2.12); {@code logyx.settlement.reminders.every=off} lo apaga (tests: se invoca a mano). */
@ApplicationScoped
public class SettlementScheduler {

    @Inject SettlementReminders reminders;

    @Scheduled(every = "{logyx.settlement.reminders.every}", identity = "settlement-reminders",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        int sent = reminders.sweep(SettlementService.today());
        if (sent > 0) Log.infof("Recordatorios de pago: %d", sent);
    }
}
