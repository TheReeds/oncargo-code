package pe.logyx.marketplace.application;

import pe.logyx.shared.tenancy.SystemTx;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.domain.model.Auction;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * RN04: cierre por tiempo. Lo invoca el scheduler sin usuario autenticado,
 * así que la transacción corre sin RLS (rol dueño): por definición cruza organizaciones.
 */
@ApplicationScoped
public class CloseExpiredAuctionsUseCase {

    @Inject SystemTx systemTx;

    @Inject AuctionRepository auctions;

    // SystemTx: job sin usuario; RN04 cierra subastas de todas las organizaciones.
    public int closeExpired() {
        return systemTx.call(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            List<Auction> expired = auctions.listOpenClosingBefore(now);
            expired.forEach(a -> { a.close(Auction.CloseReason.TIMEOUT, now); auctions.save(a); });
            if (!expired.isEmpty()) Log.infof("Subastas cerradas por tiempo: %d", expired.size());
            return expired.size();
        });
    }
}
