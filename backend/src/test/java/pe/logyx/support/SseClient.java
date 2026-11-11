package pe.logyx.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Cliente SSE mínimo para tests: abre {@code /api/v1/events/stream} y junta los mensajes recibidos. */
public final class SseClient implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public record Frame(String id, String type, Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        static Frame of(String id, String json) {
            try {
                Map<String, Object> m = JSON.readValue(json, Map.class);
                return new Frame(id, (String) m.get("type"), (Map<String, Object>) m.get("data"));
            } catch (Exception e) {
                throw new IllegalStateException(json, e);
            }
        }
    }

    private final LinkedBlockingQueue<Frame> queue = new LinkedBlockingQueue<>();
    private final List<Frame> all = new CopyOnWriteArrayList<>();
    private final AtomicInteger comments = new AtomicInteger();
    private final int status;
    private final InputStream body;
    private final Thread reader;
    private volatile boolean ended;

    private SseClient(HttpResponse<InputStream> res) {
        this.status = res.statusCode();
        this.body = res.body();
        this.reader = new Thread(this::read, "sse-test-reader");
        this.reader.setDaemon(true);
        if (status == 200) reader.start();
    }

    public static SseClient open(String baseUrl, String token, String lastEventId) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "api/v1/events/stream"))
                .header("Accept", "text/event-stream");
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (lastEventId != null) b.header("Last-Event-ID", lastEventId);
        try {
            return new SseClient(HTTP.send(b.GET().build(), HttpResponse.BodyHandlers.ofInputStream()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public int status() { return status; }
    public int comments() { return comments.get(); }
    public boolean ended() { return ended; }

    /** Todos los mensajes recibidos hasta ahora. */
    public List<Frame> frames() { return new ArrayList<>(all); }

    /** Espera el primer mensaje que cumpla {@code p} (descarta los anteriores de la cola). */
    public Frame await(Predicate<Frame> p, long millis) {
        long until = System.currentTimeMillis() + millis;
        try {
            for (Frame f : all) if (p.test(f)) return f;
            while (System.currentTimeMillis() < until) {
                Frame f = queue.poll(until - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (f != null && p.test(f)) return f;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public Frame awaitType(String type, long millis) {
        return await(f -> type.equals(f.type()), millis);
    }

    public Frame ready() {
        Frame f = await(x -> x.type() != null && x.type().startsWith("stream."), 5000);
        if (f == null) throw new AssertionError("El stream no envió stream.ready");
        return f;
    }

    private void read() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        Frame f = Frame.of(id, data.toString());
                        all.add(f);
                        queue.add(f);
                    }
                    id = null;
                    data.setLength(0);
                } else if (line.startsWith(":")) {
                    comments.incrementAndGet();
                } else if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).trim());
                }
            }
        } catch (Exception ignored) {
            // conexión cerrada
        } finally {
            ended = true;
        }
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (Exception ignored) {
            // nada
        }
        reader.interrupt();
    }
}
