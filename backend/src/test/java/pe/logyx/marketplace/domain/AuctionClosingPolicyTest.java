package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.marketplace.domain.model.Auction;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** RN04: la subasta cierra a los 20 min o al recibir 5 ofertas. */
class AuctionClosingPolicyTest {

    private final AuctionClosingPolicy policy = new AuctionClosingPolicy(Duration.ofMinutes(20), 5);
    private final OffsetDateTime t0 = OffsetDateTime.parse("2026-11-10T10:00:00-05:00");

    @Test
    void abreConCierreProgramadoYMaximoDeOfertas() {
        Auction a = policy.open(UUID.randomUUID(), t0);
        assertEquals(Auction.Status.OPEN, a.status());
        assertEquals(t0.plusMinutes(20), a.closesAt());
        assertEquals(5, a.maxBids());
        assertTrue(a.acceptsBids(t0.plusMinutes(19)));
        assertFalse(a.acceptsBids(t0.plusMinutes(20)));
    }

    @Test
    void cierraAlLlegarAlMaximoDeOfertas() {
        Auction a = policy.open(UUID.randomUUID(), t0);
        assertFalse(AuctionClosingPolicy.reachedMaxBids(a, 4));
        assertTrue(AuctionClosingPolicy.reachedMaxBids(a, 5));
    }

    @Test
    void venceExactamenteAlCumplirseElPlazo() {
        Auction a = policy.open(UUID.randomUUID(), t0);
        assertFalse(AuctionClosingPolicy.isExpired(a, t0.plusMinutes(19)));
        assertTrue(AuctionClosingPolicy.isExpired(a, t0.plusMinutes(20)));
        a.close(Auction.CloseReason.TIMEOUT, t0.plusMinutes(20));
        assertFalse(AuctionClosingPolicy.isExpired(a, t0.plusHours(1)), "una subasta cerrada no vuelve a vencer");
    }

    @Test
    void cancelarDejaLaSubastaCancelada() {
        Auction a = policy.open(UUID.randomUUID(), t0);
        a.closeIfOpen(Auction.CloseReason.CANCELLED, t0);
        assertEquals(Auction.Status.CANCELLED, a.status());
        a.closeIfOpen(Auction.CloseReason.ACCEPTED, t0);
        assertEquals(Auction.CloseReason.CANCELLED, a.closeReason(), "no se reabre ni cambia el motivo");
    }
}
