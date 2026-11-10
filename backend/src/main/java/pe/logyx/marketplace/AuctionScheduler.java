package pe.logyx.marketplace;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** RN04: cierra las subastas vencidas. Sin usuario → corre sin RLS (rol dueño). */
@ApplicationScoped
public class AuctionScheduler {

    @Inject MarketplaceService marketplace;

    @Scheduled(every = "30s", identity = "auction-closer", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void closeExpired() {
        marketplace.closeExpiredAuctions();
    }
}
