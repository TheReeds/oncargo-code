package pe.logyx.realtime.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import pe.logyx.realtime.application.port.out.RealtimeConnection;
import pe.logyx.realtime.domain.RealtimeEventTypes;
import pe.logyx.realtime.domain.model.RealtimeEvent;
import pe.logyx.realtime.domain.model.Subscriber;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conexión SSE de un usuario. Mientras se arma la reanudación queda en modo "buffer": los eventos en vivo que llegan
 * se guardan y, al terminar, se envían los que no vinieron en la reanudación (sin huecos ni duplicados en esa ventana).
 * Todas las escrituras van sincronizadas: el hub (listener) y el latido escriben desde hilos distintos.
 */
final class SseRealtimeConnection implements RealtimeConnection {

    private static final long RETRY_MS = 3000;
    private static final int MAX_BUFFER = 1000;

    private final SseEventSink sink;
    private final Sse sse;
    private final ObjectMapper json;
    private final Subscriber subscriber;
    private final Instant expiresAt;

    private boolean buffering = true;
    private final List<RealtimeEvent> buffer = new ArrayList<>();
    private volatile boolean closed;

    SseRealtimeConnection(SseEventSink sink, Sse sse, ObjectMapper json, Subscriber subscriber, Instant expiresAt) {
        this.sink = sink;
        this.sse = sse;
        this.json = json;
        this.subscriber = subscriber;
        this.expiresAt = expiresAt;
    }

    @Override public Subscriber subscriber() { return subscriber; }
    @Override public Instant expiresAt() { return expiresAt; }
    @Override public boolean isClosed() { return closed || sink.isClosed(); }

    /** Envía la reanudación, luego {@code stream.ready} (o {@code stream.resync}) y vacía el buffer. */
    synchronized void start(List<RealtimeEvent> replay, boolean overflow, long latestSeq) {
        Set<Long> sent = new HashSet<>();
        for (RealtimeEvent e : replay) {
            if (!send(e)) return;
            sent.add(e.seq());
        }
        String type = overflow ? RealtimeEventTypes.STREAM_RESYNC : RealtimeEventTypes.STREAM_READY;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("replayed", replay.size());
        if (!control(type, latestSeq, data)) return;
        buffering = false;
        for (RealtimeEvent e : buffer) {
            if (!sent.contains(e.seq()) && !send(e)) return;
        }
        buffer.clear();
    }

    @Override
    public synchronized boolean deliver(RealtimeEvent e) {
        if (isClosed()) return false;
        if (buffering) {
            if (buffer.size() >= MAX_BUFFER) return false;
            buffer.add(e);
            return true;
        }
        return send(e);
    }

    @Override
    public synchronized boolean heartbeat() {
        if (isClosed()) return false;
        return write(sse.newEventBuilder().comment("ping").build());
    }

    @Override
    public synchronized void close(String reason) {
        if (closed) return;
        if ("expired".equals(reason) && !sink.isClosed()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reason", "TOKEN_EXPIRED");
            control(RealtimeEventTypes.STREAM_EXPIRED, -1, data);
        }
        closed = true;
        try {
            sink.close();
        } catch (RuntimeException ignored) {
            // ya cerrada por el cliente
        }
    }

    private boolean send(RealtimeEvent e) {
        return write(sse.newEventBuilder()
                .id(Long.toString(e.seq()))
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(envelope(e.type(), e.seq(), e.occurredAt(), e.data()))
                .build());
    }

    private boolean control(String type, long seq, Map<String, Object> data) {
        var b = sse.newEventBuilder().mediaType(MediaType.APPLICATION_JSON_TYPE).reconnectDelay(RETRY_MS)
                .data(envelope(type, seq >= 0 ? seq : null, OffsetDateTime.now(ZoneOffset.UTC), data));
        if (seq >= 0) b.id(Long.toString(seq));
        return write(b.build());
    }

    private String envelope(String type, Long seq, OffsetDateTime at, Map<String, Object> data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("seq", seq);
        m.put("occurredAt", at == null ? null : at.toString());
        m.put("data", data);
        try {
            return json.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean write(OutboundSseEvent event) {
        if (closed || sink.isClosed()) return false;
        try {
            sink.send(event).whenComplete((ok, err) -> { if (err != null) closed = true; });
            return true;
        } catch (RuntimeException e) {
            closed = true;
            return false;
        }
    }
}
