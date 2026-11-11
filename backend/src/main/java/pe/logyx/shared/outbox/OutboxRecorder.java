package pe.logyx.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Escribe cada {@link OutboxEvent} en {@code outbox_events} <b>dentro</b> de la transacción que lo dispara
 * (si el caso de uso se revierte, el evento también). En transacciones de usuario inserta como
 * {@code logyx_app}: la política de V11 solo permite INSERT con {@code actor_org_id = app_org()} y no deja leer,
 * modificar ni borrar el outbox.
 *
 * <p>Los observadores llevan {@code @Default} para no capturar el re-disparo {@code @Outboxed} del despacho.
 *
 * <p>Tras el commit pide un despacho inmediato (camino rápido, misma latencia que los antiguos observadores
 * AFTER_SUCCESS). Si ese despacho falla o el proceso cae, el job programado lo reintenta.
 */
@ApplicationScoped
public class OutboxRecorder {

    @Inject EntityManager em;
    @Inject ObjectMapper json;
    @Inject OutboxDispatcher dispatcher;

    void record(@Observes(during = TransactionPhase.IN_PROGRESS) @Default OutboxEvent event) {
        String payload;
        try {
            payload = json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + event.getClass().getName(), e);
        }
        em.createNativeQuery("INSERT INTO outbox_events (event_type, payload) VALUES (?1, CAST(?2 AS jsonb))")
                .setParameter(1, event.getClass().getName())
                .setParameter(2, payload)
                .executeUpdate();
    }

    void afterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) @Default OutboxEvent event) {
        try {
            dispatcher.drain(OutboxDispatcher.FAST_PATH_BATCH);
        } catch (RuntimeException e) {
            // Nunca afecta a la respuesta: el evento sigue pendiente y lo toma el job.
            Log.warnf("Despacho inmediato del outbox falló (%s); se reintentará", e.getClass().getSimpleName());
        }
    }
}
