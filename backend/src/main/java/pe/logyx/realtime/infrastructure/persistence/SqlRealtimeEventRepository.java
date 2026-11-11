package pe.logyx.realtime.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import pe.logyx.realtime.application.port.out.RealtimeEventRepository;
import pe.logyx.realtime.domain.model.RealtimeEvent;
import pe.logyx.realtime.domain.model.Recipient;
import pe.logyx.realtime.domain.model.Subscriber;
import pe.logyx.shared.tenancy.OrgType;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code realtime_events} con SQL nativo (tabla de solo inserción/lectura, sin entidad). El mismo repositorio sirve
 * en la transacción de sistema del despacho (escritura, hub) y en la del usuario (reanudación bajo RLS).
 */
@ApplicationScoped
public class SqlRealtimeEventRepository implements RealtimeEventRepository {

    private static final String COLUMNS = "seq, event_type, org_id, org_type, user_id, CAST(payload AS text), created_at";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    @Inject EntityManager em;
    @Inject ObjectMapper json;

    @Override
    public void append(String type, Recipient r, Map<String, Object> data) {
        String payload;
        try {
            payload = json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento en tiempo real " + type, e);
        }
        em.createNativeQuery("INSERT INTO realtime_events (event_type, org_id, org_type, user_id, payload) "
                        + "VALUES (?1, CAST(?2 AS uuid), CAST(?3 AS varchar), CAST(?4 AS uuid), CAST(?5 AS jsonb))")
                .setParameter(1, type)
                .setParameter(2, text(r.orgId()))
                .setParameter(3, r.orgType() == null ? null : r.orgType().name())
                .setParameter(4, text(r.userId()))
                .setParameter(5, payload)
                .executeUpdate();
    }

    @Override
    public List<RealtimeEvent> findBySeqs(Collection<Long> seqs) {
        if (seqs.isEmpty()) return List.of();
        String array = seqs.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
        return rows(em.createNativeQuery("SELECT " + COLUMNS + " FROM realtime_events "
                + "WHERE seq = ANY(CAST(?1 AS bigint[])) ORDER BY seq").setParameter(1, array));
    }

    @Override
    public List<RealtimeEvent> after(long after, int limit) {
        return rows(em.createNativeQuery("SELECT " + COLUMNS + " FROM realtime_events WHERE seq > ?1 ORDER BY seq LIMIT ?2")
                .setParameter(1, after).setParameter(2, limit));
    }

    @Override
    public List<RealtimeEvent> visibleAfter(Subscriber s, long after, int limit) {
        return rows(audience(em.createNativeQuery("SELECT " + COLUMNS + " FROM realtime_events WHERE seq > :after AND "
                + AUDIENCE + " ORDER BY seq LIMIT :lim").setParameter("after", after).setParameter("lim", limit), s));
    }

    @Override
    public long latestVisible(Subscriber s) {
        Object v = audience(em.createNativeQuery("SELECT coalesce(max(seq), 0) FROM realtime_events WHERE " + AUDIENCE), s)
                .getSingleResult();
        return ((Number) v).longValue();
    }

    @Override
    public int deleteOlderThan(OffsetDateTime before) {
        return em.createNativeQuery("DELETE FROM realtime_events WHERE created_at < ?1").setParameter(1, before)
                .executeUpdate();
    }

    /**
     * Defensa en profundidad sobre la política RLS: la misma regla que {@code RealtimeAudiencePolicy}.
     * Parámetros con nombre: org, tipo y usuario del suscriptor, y si es conductor.
     */
    private static final String AUDIENCE = """
            ((org_id = :aorg AND ((:adriver = false AND user_id IS NULL) OR (user_id = :auser)))
             OR (:adriver = false AND org_id IS NULL AND user_id IS NULL AND org_type = :atype))""";

    private static String text(UUID id) {
        return id == null ? null : id.toString();
    }

    private static Query audience(Query q, Subscriber s) {
        return q.setParameter("aorg", s.orgId()).setParameter("atype", s.orgType().name())
                .setParameter("auser", s.userId()).setParameter("adriver", s.driver());
    }

    @SuppressWarnings("unchecked")
    private List<RealtimeEvent> rows(Query q) {
        return ((List<Object[]>) q.getResultList()).stream().map(this::toEvent).toList();
    }

    private RealtimeEvent toEvent(Object[] r) {
        Map<String, Object> data;
        try {
            data = json.readValue((String) r[5], MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Evento en tiempo real ilegible", e);
        }
        Recipient to = new Recipient((UUID) r[2], r[3] == null ? null : OrgType.valueOf((String) r[3]), (UUID) r[4]);
        return new RealtimeEvent(((Number) r[0]).longValue(), (String) r[1], to, data, time(r[6]));
    }

    private static OffsetDateTime time(Object v) {
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof Instant i) return i.atOffset(ZoneOffset.UTC);
        if (v instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
