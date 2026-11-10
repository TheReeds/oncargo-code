package pe.logyx.marketplace.domain;

import pe.logyx.marketplace.domain.model.Auction;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * RN04: la subasta cierra a los {@code duration} (20 min por defecto) o al
 * recibir {@code maxBids} ofertas (5 por defecto), lo que ocurra primero.
 */
public final class AuctionClosingPolicy {

    private final Duration duration;
    private final int maxBids;

    public AuctionClosingPolicy(Duration duration, int maxBids) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("duración inválida");
        if (maxBids < 1) throw new IllegalArgumentException("maxBids debe ser ≥ 1");
        this.duration = duration;
        this.maxBids = maxBids;
    }

    /** Abre la subasta de una solicitud recién publicada. */
    public Auction open(java.util.UUID requestId, OffsetDateTime now) {
        return Auction.open(requestId, now, now.plus(duration), maxBids);
    }

    /** Cierre por número de ofertas: {@code totalBids} incluye la recién recibida. */
    public static boolean reachedMaxBids(Auction auction, long totalBids) {
        return totalBids >= auction.maxBids();
    }

    /** Cierre por tiempo: abierta y con {@code closesAt <= now}. */
    public static boolean isExpired(Auction auction, OffsetDateTime now) {
        return auction.isOpen() && !auction.closesAt().isAfter(now);
    }

    public Duration duration() { return duration; }
    public int maxBids() { return maxBids; }
}
