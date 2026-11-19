package pe.logyx.fleet.infrastructure.scheduling;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.fleet.application.FleetDocumentService;

/** Barrido de vencimientos de documentos de flota (2.4); {@code logyx.fleet.documents.every=off} lo apaga (tests). */
@ApplicationScoped
public class FleetDocumentScheduler {

    @Inject FleetDocumentService documents;

    @Scheduled(every = "{logyx.fleet.documents.every}", identity = "fleet-document-expiry",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        int sent = documents.sweepExpiring(FleetDocumentService.today());
        if (sent > 0) Log.infof("Documentos de flota por vencer: %d avisos", sent);
    }
}
