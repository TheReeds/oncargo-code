package pe.logyx.outbox;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.logyx.marketplace.application.event.BidPlaced;
import pe.logyx.shared.outbox.OutboxDispatcher;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Outbox transaccional (ADR-0006): el evento se escribe con el caso de uso, se despacha tras el commit y, si el
 * despacho falla, queda pendiente y se reintenta sin duplicar ni perder notificaciones.
 */
@QuarkusTest
class OutboxTest {

    @Inject EntityManager em;
    @Inject SystemTx systemTx;
    @Inject OutboxDispatcher dispatcher;
    @Inject Event<BidPlaced> bidPlaced;

    @AfterEach
    void reset() {
        FailingOutboxConsumer.reset();
    }

    /** status, attempts, last_error y actor del evento BidPlaced de la oferta. */
    private Object[] bidPlacedRow(String bidId) {
        return systemTx.call(() -> (Object[]) em.createNativeQuery("""
                SELECT status, attempts, coalesce(last_error, ''), CAST(actor_org_id AS text),
                       available_at > now()
                FROM outbox_events WHERE event_type LIKE '%.BidPlaced' AND payload->>'bidId' = ?1""")
                .setParameter(1, bidId).getSingleResult());
    }

    private void makeAvailable(String bidId) {
        systemTx.run(() -> em.createNativeQuery("UPDATE outbox_events SET available_at = now() "
                + "WHERE event_type LIKE '%.BidPlaced' AND payload->>'bidId' = ?1").setParameter(1, bidId).executeUpdate());
    }

    private static int notifications(Session s) {
        return Api.as(s).get("/api/v1/notifications").then().statusCode(200).extract().path("meta.total");
    }

    @Test
    void elEventoSeEscribeConElCasoDeUsoYSeDespachaTrasElCommit() {
        Flows.Bidding b = Flows.bidding("Outbox Ok");
        Object[] row = bidPlacedRow(b.bidId());
        assertEquals("DONE", row[0]);
        assertEquals(1, ((Number) row[1]).intValue());
        assertEquals(b.carrier().orgId(), row[3], "actor = organización del contexto RLS");
        assertEquals(1, notifications(b.pyme()));
    }

    @Test
    void siLaTransaccionDeNegocioSeRevierteNoQuedaEvento() {
        Session pyme = register("PYME", "Outbox Rollback");
        java.util.UUID org = java.util.UUID.fromString(pyme.orgId());
        java.util.UUID bidId = java.util.UUID.randomUUID();
        BidPlaced e = new BidPlaced(bidId, java.util.UUID.randomUUID(), org, org, "x", java.math.BigDecimal.TEN, "A → B", 1, false);
        assertThrows(IllegalStateException.class, () -> systemTx.run(() -> {
            bidPlaced.fire(e);
            throw new IllegalStateException("el caso de uso falla después de publicar");
        }));
        long events = systemTx.call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM outbox_events WHERE payload->>'bidId' = ?1")
                .setParameter(1, bidId.toString()).getSingleResult()).longValue());
        assertEquals(0, events);
        assertEquals(0, notifications(pyme));
    }

    @Test
    void siElDespachoFallaNoSePierdeYSeReintentaSinDuplicar() {
        FailingOutboxConsumer.failNext("Outbox Falla", 2);
        Flows.Bidding b = Flows.bidding("Outbox Falla");      // la oferta se confirma igual (201)

        Object[] row = bidPlacedRow(b.bidId());
        assertEquals("PENDING", row[0]);
        assertEquals(1, ((Number) row[1]).intValue());
        assertTrue(((String) row[2]).contains("fallo simulado"), (String) row[2]);
        assertEquals(Boolean.TRUE, row[4], "reintento con espera");
        assertEquals(0, notifications(b.pyme()), "la notificación parcial se revirtió");

        dispatcher.drain(50);                                   // aún no vence la espera: no se toca
        assertEquals(1, ((Number) bidPlacedRow(b.bidId())[1]).intValue());

        makeAvailable(b.bidId());
        dispatcher.drain(50);                                   // segundo fallo
        assertEquals(2, ((Number) bidPlacedRow(b.bidId())[1]).intValue());
        assertEquals(0, notifications(b.pyme()));

        makeAvailable(b.bidId());
        dispatcher.drain(50);                                   // ahora sí
        row = bidPlacedRow(b.bidId());
        assertEquals("DONE", row[0]);
        assertEquals(3, ((Number) row[1]).intValue());
        assertEquals(1, notifications(b.pyme()));

        makeAvailable(b.bidId());
        dispatcher.drain(50);                                   // idempotente: DONE no se vuelve a entregar
        assertEquals(1, notifications(b.pyme()));
    }

    @Test
    void agotadosLosIntentosQuedaComoFailed() {
        FailingOutboxConsumer.failNext("Outbox Dead", 1);
        Flows.Bidding b = Flows.bidding("Outbox Dead");
        systemTx.run(() -> em.createNativeQuery("UPDATE outbox_events SET attempts = 9, available_at = now() "
                + "WHERE payload->>'bidId' = ?1").setParameter(1, b.bidId()).executeUpdate());
        FailingOutboxConsumer.failNext("Outbox Dead", 1);
        dispatcher.drain(50);
        Object[] row = bidPlacedRow(b.bidId());
        assertEquals("FAILED", row[0]);
        assertEquals(10, ((Number) row[1]).intValue());
        dispatcher.drain(50);
        assertEquals("FAILED", bidPlacedRow(b.bidId())[0]);
        assertEquals(0, notifications(b.pyme()));
    }

    @Test
    void unEventoBloqueadoPorOtroDespachadorSeSalta() throws Exception {
        FailingOutboxConsumer.failNext("Outbox Lock", 1);
        Flows.Bidding b = Flows.bidding("Outbox Lock");
        makeAvailable(b.bidId());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> systemTx.run(() -> {
            em.createNativeQuery("SELECT id FROM outbox_events WHERE payload->>'bidId' = ?1 FOR UPDATE")
                    .setParameter(1, b.bidId()).getResultList();
            locked.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(locked.await(20, TimeUnit.SECONDS));
        long start = System.nanoTime();
        dispatcher.drain(50);                                   // SKIP LOCKED: no espera ni lo procesa
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10);
        assertEquals("PENDING", bidPlacedRow(b.bidId())[0]);
        release.countDown();
        holder.get(20, TimeUnit.SECONDS);

        dispatcher.drain(50);
        assertEquals("DONE", bidPlacedRow(b.bidId())[0]);
        assertEquals(1, notifications(b.pyme()));
    }

    @Test
    void unUsuarioNoPuedeLeerNiFalsificarElOutbox() {
        Session a = register("CARRIER", "Outbox RLS A");
        Session other = register("PYME", "Outbox RLS B");
        Exception read = assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            asUser(a);
            em.createNativeQuery("SELECT count(*) FROM outbox_events").getSingleResult();
        }));
        assertTrue(root(read).contains("permission denied"), root(read));
        Exception forged = assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            asUser(a);
            em.createNativeQuery("INSERT INTO outbox_events (event_type, payload, actor_org_id) VALUES "
                    + "('pe.logyx.marketplace.application.event.BidPlaced', '{}', CAST(?1 AS uuid))")
                    .setParameter(1, other.orgId()).executeUpdate();
        }));
        assertTrue(root(forged).contains("row-level security"), root(forged));
    }

    private void asUser(Session s) {
        em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', 'CARRIER', true)")
                .setParameter(1, s.orgId()).getSingleResult();
        em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
    }

    private static String root(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }
}
