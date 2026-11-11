package pe.logyx.outbox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import pe.logyx.marketplace.application.event.BidPlaced;
import pe.logyx.shared.outbox.Outboxed;

import java.util.concurrent.atomic.AtomicInteger;

/** Solo tests: simula un consumidor del outbox que falla N veces para eventos de un transportista dado. */
@ApplicationScoped
public class FailingOutboxConsumer {

    static final AtomicInteger FAILURES = new AtomicInteger();
    static volatile String carrierTag = "\u0000";

    static void failNext(String tag, int times) {
        carrierTag = tag;
        FAILURES.set(times);
    }

    static void reset() {
        FAILURES.set(0);
        carrierTag = "\u0000";
    }

    void on(@Observes @Outboxed BidPlaced e) {
        if (e.carrierName().contains(carrierTag) && FAILURES.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("fallo simulado del consumidor");
        }
    }
}
