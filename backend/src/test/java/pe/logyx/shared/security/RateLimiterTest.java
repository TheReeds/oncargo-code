package pe.logyx.shared.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void limitaPorVentanaYSeReinicia() {
        MutableClock clock = new MutableClock();
        RateLimiter l = new RateLimiter(clock);
        Duration w = Duration.ofMinutes(15);
        for (int i = 0; i < 3; i++) assertTrue(l.tryAcquire("k", 3, w));
        assertFalse(l.tryAcquire("k", 3, w));
        assertTrue(l.tryAcquire("otra", 3, w), "las claves son independientes");
        assertEquals(900, l.retryAfterSeconds("k", w));
        clock.now = clock.now.plus(Duration.ofMinutes(15));
        assertTrue(l.tryAcquire("k", 3, w), "ventana nueva");
    }

    @Test
    void exhaustedNoCuentaYResetLimpia() {
        RateLimiter l = new RateLimiter(new MutableClock());
        Duration w = Duration.ofMinutes(1);
        assertFalse(l.exhausted("e", 2, w));
        l.record("e", w);
        l.record("e", w);
        assertTrue(l.exhausted("e", 2, w));
        assertTrue(l.exhausted("e", 2, w));
        l.reset("e");
        assertFalse(l.exhausted("e", 2, w));
    }
}
