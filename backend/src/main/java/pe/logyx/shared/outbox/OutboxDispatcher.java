package pe.logyx.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Despacha el outbox transaccional (ADR-0006): toma eventos {@code PENDING} vencidos con
 * {@code FOR UPDATE SKIP LOCKED} (varias instancias pueden correr a la vez sin pisarse), los deserializa y los
 * dispara como {@code @Outboxed} a sus consumidores (hoy notification y reputation), y los marca {@code DONE}
 * <b>en la misma transacción</b>: o se crean las notificaciones y se marca el evento, o no pasa nada.
 *
 * <p>Si un consumidor falla, la transacción se revierte y en otra se suma el intento con espera exponencial
 * ({@code logyx.outbox.retry-base} × 2^intentos, tope 1 h). Tras {@code logyx.outbox.max-attempts} queda
 * {@code FAILED} (dead letter, revisar a mano). Los {@code DONE} se conservan {@code logyx.outbox.retention}
 * para que la fase de tiempo real (SSE/WebSocket) pueda reanudar desde {@code seq} (Last-Event-ID).
 *
 * <p>SystemTx justificado: el outbox cruza organizaciones (el evento de una organización notifica a otra).
 */
@ApplicationScoped
public class OutboxDispatcher {

    static final int FAST_PATH_BATCH = 20;
    private static final int JOB_BATCH = 200;
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    @Inject EntityManager em;
    @Inject ObjectMapper json;
    @Inject SystemTx systemTx;
    @Inject @Outboxed Event<Object> delivery;

    @ConfigProperty(name = "logyx.outbox.max-attempts", defaultValue = "10")
    int maxAttempts;

    @ConfigProperty(name = "logyx.outbox.retry-base", defaultValue = "PT10S")
    Duration retryBase;

    @ConfigProperty(name = "logyx.outbox.retention", defaultValue = "P7D")
    Duration retention;

    @Scheduled(every = "{logyx.outbox.every}", identity = "outbox-dispatcher",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        int n = drain(JOB_BATCH);
        if (n > 0) Log.infof("Outbox: %d eventos despachados por el job", n);
    }

    @Scheduled(every = "{logyx.outbox.cleanup-every}", identity = "outbox-cleanup", delayed = "1m",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void cleanup() {
        int n = systemTx.call(() -> em.createNativeQuery(
                        "DELETE FROM outbox_events WHERE status = 'DONE' AND processed_at < now() - make_interval(secs => ?1)")
                .setParameter(1, (double) retention.toSeconds()).executeUpdate());
        if (n > 0) Log.infof("Outbox: %d eventos antiguos eliminados", n);
    }

    /** Despacha hasta {@code max} eventos pendientes; devuelve cuántos quedaron {@code DONE}. */
    public int drain(int max) {
        int done = 0;
        for (int i = 0; i < max; i++) {
            Outcome o = dispatchNext();
            if (o == Outcome.EMPTY) break;
            if (o == Outcome.DONE) done++;
        }
        return done;
    }

    enum Outcome { EMPTY, DONE, FAILED }

    private Outcome dispatchNext() {
        AtomicReference<UUID> claimed = new AtomicReference<>();
        try {
            return systemTx.call(() -> {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, event_type, CAST(payload AS text) FROM outbox_events
                        WHERE status = 'PENDING' AND available_at <= now()
                        ORDER BY seq LIMIT 1 FOR UPDATE SKIP LOCKED""").getResultList();
                if (rows.isEmpty()) return Outcome.EMPTY;
                Object[] r = rows.get(0);
                UUID id = (UUID) r[0];
                claimed.set(id);
                delivery.fire(deserialize((String) r[1], (String) r[2]));
                em.createNativeQuery("UPDATE outbox_events SET status = 'DONE', processed_at = now(), "
                        + "attempts = attempts + 1, last_error = NULL WHERE id = ?1").setParameter(1, id).executeUpdate();
                return Outcome.DONE;
            });
        } catch (RuntimeException e) {
            UUID id = claimed.get();
            if (id == null) throw e;
            recordFailure(id, e);
            return Outcome.FAILED;
        }
    }

    private void recordFailure(UUID id, RuntimeException error) {
        String message = rootMessage(error);
        systemTx.run(() -> {
            Number attempts = (Number) em.createNativeQuery(
                    "SELECT attempts FROM outbox_events WHERE id = ?1 FOR UPDATE").setParameter(1, id).getSingleResult();
            int next = attempts.intValue() + 1;
            long delay = Math.min(MAX_BACKOFF.toSeconds(), retryBase.toSeconds() * (1L << Math.min(next - 1, 20)));
            em.createNativeQuery("UPDATE outbox_events SET attempts = ?2, last_error = ?3, "
                            + "available_at = now() + make_interval(secs => ?4), status = ?5 WHERE id = ?1")
                    .setParameter(1, id).setParameter(2, next).setParameter(3, message)
                    .setParameter(4, (double) delay)
                    .setParameter(5, next >= maxAttempts ? "FAILED" : "PENDING")
                    .executeUpdate();
            if (next >= maxAttempts) Log.errorf("Outbox: evento %s FAILED tras %d intentos: %s", id, next, message);
            else Log.warnf("Outbox: evento %s falló (intento %d), reintento en %d s: %s", id, next, delay, message);
        });
    }

    private Object deserialize(String type, String payload) {
        try {
            Class<?> cls = Class.forName(type, false, Thread.currentThread().getContextClassLoader());
            if (!OutboxEvent.class.isAssignableFrom(cls) || !cls.getName().startsWith("pe.logyx.")) {
                throw new IllegalStateException("Tipo de evento no admitido en el outbox: " + type);
            }
            return json.readValue(payload, cls);
        } catch (ClassNotFoundException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer el evento " + type, e);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        String m = c.getClass().getSimpleName() + ": " + c.getMessage();
        return m.length() > 1000 ? m.substring(0, 1000) : m;
    }
}
