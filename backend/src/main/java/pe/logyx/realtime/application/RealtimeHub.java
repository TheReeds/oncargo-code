package pe.logyx.realtime.application;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.realtime.application.port.out.RealtimeConnection;
import pe.logyx.realtime.application.port.out.RealtimeEventRepository;
import pe.logyx.realtime.domain.RealtimeAudiencePolicy;
import pe.logyx.realtime.domain.model.RealtimeEvent;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conexiones SSE abiertas en <b>esta</b> instancia y su despacho (ADR-0007).
 *
 * <p>Cada instancia escucha {@code LISTEN logyx_realtime} (adaptador {@code PgRealtimeListener}); al confirmarse una
 * proyección llega el {@code seq}, el hub lee esas filas <b>una vez</b> y las entrega solo a las conexiones que
 * cumplen {@link RealtimeAudiencePolicy} (organización / usuario / tipo, sale del JWT). Así funciona con varias
 * réplicas sin infraestructura nueva. El payload del NOTIFY es solo un aviso: se lee la fila real, así que un NOTIFY
 * forjado no puede inventar eventos ni cambiar su destinatario.
 *
 * <p>SystemTx justificado: el hilo del listener no tiene usuario; lee filas ya dirigidas y filtra en memoria por
 * destinatario con la misma regla que la política RLS {@code realtime_events_read} (la reanudación sí lee como el
 * usuario, ver {@link RealtimeStreamService}).
 *
 * <p>Latido cada {@code logyx.realtime.heartbeat} (comentario SSE, mantiene vivos proxies y detecta caídas), máximo
 * {@code logyx.realtime.max-connections-per-user} conexiones por usuario y cierre limpio ({@code stream.expired}) al
 * vencer el JWT con el que se abrió.
 */
@ApplicationScoped
public class RealtimeHub {

    @Inject RealtimeEventRepository events;
    @Inject SystemTx system;

    @ConfigProperty(name = "logyx.realtime.heartbeat", defaultValue = "PT20S")
    Duration heartbeat;

    @ConfigProperty(name = "logyx.realtime.max-connections-per-user", defaultValue = "5")
    int maxPerUser;

    private final Set<RealtimeConnection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastSeq = new AtomicLong();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "realtime-heartbeat");
            t.setDaemon(true);
            return t;
        });
        long every = Math.max(200, heartbeat.toMillis());
        scheduler.scheduleAtFixedRate(this::tick, every, every, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        connections.forEach(c -> c.close("shutdown"));
        connections.clear();
    }

    /** Registra una conexión o lanza 429 {@code LOGYX-RTM-001} si el usuario ya tiene el máximo abiertas. */
    public synchronized void register(RealtimeConnection c) {
        UUID user = c.subscriber().userId();
        connections.removeIf(RealtimeConnection::isClosed);
        long open = connections.stream().filter(x -> x.subscriber().userId().equals(user)).count();
        if (open >= maxPerUser) {
            throw new BusinessException(429, "LOGYX-RTM-001",
                    "Tienes demasiadas conexiones en vivo abiertas. Cierra otras pestañas o dispositivos e intenta de nuevo.");
        }
        connections.add(c);
        long delay = Duration.between(Instant.now(), c.expiresAt()).toMillis();
        scheduler.schedule(() -> expire(c), Math.max(0, delay), TimeUnit.MILLISECONDS);
    }

    public void unregister(RealtimeConnection c) {
        connections.remove(c);
    }

    /** Conexiones abiertas en esta instancia (métricas y tests). */
    public int openConnections() {
        connections.removeIf(RealtimeConnection::isClosed);
        return connections.size();
    }

    /** Aviso del listener: se confirmaron estas filas. */
    public void onCommitted(Collection<Long> seqs) {
        if (seqs.isEmpty()) return;
        seqs.forEach(s -> lastSeq.accumulateAndGet(s, Math::max));
        if (connections.isEmpty()) return;
        dispatch(system.call(() -> events.findBySeqs(seqs)));
    }

    /** El listener se reconectó: entrega lo confirmado mientras no escuchaba (mejor esfuerzo). */
    public void catchUp() {
        long from = lastSeq.get();
        if (from == 0 || connections.isEmpty()) return;
        List<RealtimeEvent> missed = system.call(() -> events.after(from, 1000));
        missed.forEach(e -> lastSeq.accumulateAndGet(e.seq(), Math::max));
        dispatch(missed);
        if (!missed.isEmpty()) Log.infof("Tiempo real: %d eventos entregados tras reconectar el listener", missed.size());
    }

    void dispatch(List<RealtimeEvent> list) {
        list.stream().sorted(Comparator.comparingLong(RealtimeEvent::seq)).forEach(e -> {
            for (RealtimeConnection c : connections) {
                if (RealtimeAudiencePolicy.matches(e.recipient(), c.subscriber()) && !c.deliver(e)) drop(c);
            }
        });
    }

    private void tick() {
        try {
            Instant now = Instant.now();
            for (RealtimeConnection c : connections) {
                if (c.isClosed()) drop(c);
                else if (!now.isBefore(c.expiresAt())) expire(c);
                else if (!c.heartbeat()) drop(c);
            }
        } catch (RuntimeException e) {
            Log.warnf("Tiempo real: fallo en el latido (%s)", e.getClass().getSimpleName());
        }
    }

    private void expire(RealtimeConnection c) {
        if (!connections.contains(c)) return;
        c.close("expired");
        connections.remove(c);
    }

    private void drop(RealtimeConnection c) {
        connections.remove(c);
        if (!c.isClosed()) c.close("gone");
    }
}
