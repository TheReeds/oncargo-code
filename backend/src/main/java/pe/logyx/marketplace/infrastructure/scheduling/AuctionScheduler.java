package pe.logyx.marketplace.infrastructure.scheduling;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.CloseExpiredAuctionsUseCase;

/** RN04: cierra las subastas vencidas. Sin usuario → corre sin RLS (rol dueño). */
@ApplicationScoped
public class AuctionScheduler {

    @Inject CloseExpiredAuctionsUseCase closeExpired;

    @Scheduled(every = "30s", identity = "auction-closer", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void closeExpired() {
        closeExpired.closeExpired();
    }
}
