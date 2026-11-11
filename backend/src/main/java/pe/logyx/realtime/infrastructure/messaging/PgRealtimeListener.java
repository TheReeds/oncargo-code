package pe.logyx.realtime.infrastructure.messaging;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import pe.logyx.realtime.application.RealtimeHub;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fan-out entre réplicas con {@code LISTEN/NOTIFY} de PostgreSQL (ADR-0007), sin infraestructura nueva. Un hilo por
 * instancia mantiene una conexión dedicada del datasource {@code app} ({@code logyx_api}: LISTEN no requiere
 * privilegios) y pasa los {@code seq} confirmados a {@link RealtimeHub}. Si la conexión cae, reintenta con espera y
 * al volver pide al hub que entregue lo confirmado mientras no escuchaba.
 *
 * <p>El NOTIFY solo es un aviso (cualquier rol podría emitirlo en el canal): el hub lee la fila real y respeta su
 * destinatario, así que un aviso falso no filtra ni inventa nada.
 */
@ApplicationScoped
public class PgRealtimeListener {

    static final String CHANNEL = "logyx_realtime";
    private static final int MAX_SEQS_PER_BATCH = 500;

    @Inject @DataSource("app") AgroalDataSource dataSource;
    @Inject RealtimeHub hub;

    @ConfigProperty(name = "logyx.realtime.enabled", defaultValue = "true")
    boolean enabled;

    private volatile boolean running;
    private volatile Connection current;
    private Thread thread;

    void start(@Observes StartupEvent ev) {
        if (!enabled) return;
        running = true;
        thread = new Thread(this::loop, "realtime-listener");
        thread.setDaemon(true);
        thread.start();
    }

    void stop(@Observes ShutdownEvent ev) {
        running = false;
        Connection c = current;
        if (c != null) {
            try { c.close(); } catch (SQLException ignored) { /* apagando */ }
        }
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        boolean reconnect = false;
        long backoff = 500;
        while (running) {
            try (Connection c = dataSource.getConnection()) {
                current = c;
                c.setAutoCommit(true);
                try (Statement st = c.createStatement()) {
                    st.execute("LISTEN " + CHANNEL);
                }
                PGConnection pg = c.unwrap(PGConnection.class);
                if (reconnect) {
                    Log.info("Tiempo real: listener reconectado");
                    hub.catchUp();
                }
                backoff = 500;
                while (running) {
                    PGNotification[] batch = pg.getNotifications(5000);
                    if (batch == null || batch.length == 0) continue;
                    Set<Long> seqs = new LinkedHashSet<>();
                    for (PGNotification n : batch) {
                        if (!CHANNEL.equals(n.getName())) continue;
                        try {
                            seqs.add(Long.parseLong(n.getParameter().trim()));
                        } catch (NumberFormatException ignored) {
                            // aviso mal formado: se ignora
                        }
                        if (seqs.size() >= MAX_SEQS_PER_BATCH) break;
                    }
                    try {
                        hub.onCommitted(seqs);
                    } catch (RuntimeException e) {
                        Log.warnf("Tiempo real: no se pudo despachar %d eventos (%s)", seqs.size(), e.getMessage());
                    }
                }
            } catch (SQLException | RuntimeException e) {
                if (!running) break;
                Log.warnf("Tiempo real: listener desconectado (%s); reintento en %d ms", e.getMessage(), backoff);
                reconnect = true;
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoff = Math.min(backoff * 2, 15_000);
            } finally {
                current = null;
            }
        }
    }
}
