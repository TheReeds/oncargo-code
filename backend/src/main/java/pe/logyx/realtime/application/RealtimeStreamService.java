package pe.logyx.realtime.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.realtime.application.port.out.RealtimeEventRepository;
import pe.logyx.realtime.domain.model.RealtimeEvent;
import pe.logyx.realtime.domain.model.Subscriber;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.List;

/** Apertura de un stream: quién es el suscriptor (JWT) y reanudación desde Last-Event-ID leyendo como el usuario. */
@ApplicationScoped
public class RealtimeStreamService {

    @Inject TenantContext tenant;
    @Inject RealtimeEventRepository events;

    @ConfigProperty(name = "logyx.realtime.replay-limit", defaultValue = "500")
    int replayLimit;

    /** {@code events} a reenviar; {@code overflow} = había más que el límite (el cliente debe recargar todo). */
    public record Replay(List<RealtimeEvent> events, boolean overflow, long latestSeq) {}

    public Subscriber currentSubscriber() {
        return new Subscriber(tenant.profileId(), tenant.orgId(), tenant.orgType(), tenant.isDriver());
    }

    /**
     * Bajo RLS ({@code @Transactional} con el JWT): solo filas de la organización del usuario o de su tipo, y además
     * filtradas por destinatario (conductor → solo las dirigidas a él). {@code after == null} → sin reenvío.
     */
    @Transactional
    public Replay replay(Subscriber s, Long after) {
        long latest = events.latestVisible(s);
        if (after == null || after < 0) return new Replay(List.of(), false, latest);
        List<RealtimeEvent> rows = events.visibleAfter(s, after, replayLimit + 1);
        boolean overflow = rows.size() > replayLimit;
        return new Replay(overflow ? List.of() : rows, overflow, latest);
    }
}
