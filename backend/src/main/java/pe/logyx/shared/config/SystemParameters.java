package pe.logyx.shared.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.config.domain.ParameterKey;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Parámetros de sistema editables por el operador (V18). Lectura para cualquier módulo con caché corta; escritura
 * solo del operador, con versión, motivo, historial y auditoría.
 *
 * <p><b>Lectura.</b> {@link #commissionRate()}, {@link #assignmentSla()}, {@link #incidentWarn()} y
 * {@link #incidentBreach()} se pueden llamar desde cualquier caso de uso (con o sin usuario, dentro o fuera de una
 * transacción). Leen la tabla por {@code SystemTx} (datasource privilegiado; justificación: pueden consultarse sin
 * usuario —endpoints públicos, jobs— y son datos de la plataforma, no de una organización) y los guardan
 * {@code logyx.parameters.cache-ttl} (10 s); una escritura invalida la caché de esta instancia al confirmar la
 * transacción (las demás réplicas se ponen al día al vencer el TTL).
 *
 * <p><b>Valor por omisión.</b> Mientras el operador no cambie un parámetro rige la configuración
 * ({@code logyx.commission.default-rate}, {@code logyx.operator.sla}, {@code logyx.operator.incident-warn|breach}),
 * así que un entorno que fija p. ej. {@code logyx.operator.sla=PT0S} sigue funcionando.
 */
@ApplicationScoped
public class SystemParameters {

    @Inject SystemTx systemTx;
    @Inject TenantContext tenant;
    @Inject AuditService audit;
    @Inject EntityManager em;
    @Inject TransactionSynchronizationRegistry txRegistry;

    @ConfigProperty(name = "logyx.commission.default-rate", defaultValue = "0.08") BigDecimal defaultCommission;
    @ConfigProperty(name = "logyx.operator.sla", defaultValue = "PT24H") Duration defaultSla;
    @ConfigProperty(name = "logyx.operator.incident-warn", defaultValue = "PT12H") Duration defaultIncidentWarn;
    @ConfigProperty(name = "logyx.operator.incident-breach", defaultValue = "PT24H") Duration defaultIncidentBreach;
    @ConfigProperty(name = "logyx.parameters.cache-ttl", defaultValue = "PT10S") Duration cacheTtl;

    private record Cached(Map<ParameterKey, ParameterValue> values, long loadedAt) {}

    private volatile Cached cache;

    // ----- Lectura

    /** Comisión vigente (fracción 0–0.30; 0.08 = 8 %) que se guarda en el envío al adjudicar. */
    public BigDecimal commissionRate() { return ParameterKey.COMMISSION_RATE.decimal(get(ParameterKey.COMMISSION_RATE).value()); }

    /** RN10: tiempo sin ofertas tras el cual el operador asigna manualmente. */
    public Duration assignmentSla() { return ParameterKey.ASSIGNMENT_SLA_HOURS.hours(get(ParameterKey.ASSIGNMENT_SLA_HOURS).value()); }

    /** RN11: antigüedad de una incidencia activa desde la que se muestra ámbar. */
    public Duration incidentWarn() { return ParameterKey.INCIDENT_WARN_HOURS.hours(get(ParameterKey.INCIDENT_WARN_HOURS).value()); }

    /** RN11: antigüedad de una incidencia activa desde la que se muestra rojo (SLA incumplido). */
    public Duration incidentBreach() { return ParameterKey.INCIDENT_BREACH_HOURS.hours(get(ParameterKey.INCIDENT_BREACH_HOURS).value()); }

    public ParameterValue get(ParameterKey key) { return snapshot().get(key); }

    /** Todos los parámetros con su valor efectivo, en el orden del enum. */
    public List<ParameterValue> all() {
        Map<ParameterKey, ParameterValue> m = snapshot();
        return Arrays.stream(ParameterKey.values()).map(m::get).toList();
    }

    private Map<ParameterKey, ParameterValue> snapshot() {
        Cached c = cache;
        long now = System.currentTimeMillis();
        if (c != null && now - c.loadedAt() < cacheTtl.toMillis()) return c.values();
        // SystemTx: lectura de datos de plataforma, también sin usuario (público, jobs).
        List<SystemParameterEntity> rows = systemTx.call(() -> SystemParameterEntity.<SystemParameterEntity>listAll());
        Map<String, SystemParameterEntity> byKey = new HashMap<>();
        rows.forEach(r -> byKey.put(r.key, r));
        Map<ParameterKey, ParameterValue> values = new EnumMap<>(ParameterKey.class);
        for (ParameterKey k : ParameterKey.values()) values.put(k, effective(k, byKey.get(k.key())));
        cache = new Cached(values, now);
        return values;
    }

    private ParameterValue effective(ParameterKey k, SystemParameterEntity row) {
        String def = defaultOf(k);
        if (row == null) return new ParameterValue(k, def, def, 0, null, null, null);
        return new ParameterValue(k, row.value, def, row.version, row.reason, row.updatedBy, row.updatedAt);
    }

    String defaultOf(ParameterKey k) {
        return switch (k) {
            case COMMISSION_RATE -> k.normalize(defaultCommission.toPlainString());
            case ASSIGNMENT_SLA_HOURS -> String.valueOf(defaultSla.toHours());
            case INCIDENT_WARN_HOURS -> String.valueOf(defaultIncidentWarn.toHours());
            case INCIDENT_BREACH_HOURS -> String.valueOf(defaultIncidentBreach.toHours());
        };
    }

    // ----- Escritura (operador)

    /**
     * Cambia un parámetro. Solo el operador (también lo exige RLS). {@code expectedVersion} (opcional) evita pisar el
     * cambio de otro operador: si no coincide con la versión actual (0 = valor por omisión) → 409
     * {@code LOGYX-PAR-004}. Sube la versión, guarda el historial y audita {@code SYSTEM_PARAMETER_CHANGED}.
     */
    @Transactional
    public ParameterValue update(ParameterKey key, String rawValue, String reason, Integer expectedVersion) {
        if (tenant.orgType() != OrgType.OPERATOR) {
            throw BusinessException.forbidden("Solo el operador de OnCargo puede cambiar los parámetros del sistema");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) {
            throw BusinessException.badRequest("LOGYX-PAR-002", "Indica el motivo del cambio", "reason");
        }
        String value = key.normalize(rawValue);
        // Un cambio a la vez por parámetro (también cuando aún no hay fila): bloqueo consultivo hasta el commit.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?1))").setParameter(1, "param:" + key.key())
                .getSingleResult();

        SystemParameterEntity row = SystemParameterEntity.findById(key.key());
        int current = row == null ? 0 : row.version;
        if (expectedVersion != null && expectedVersion != current) {
            throw BusinessException.conflict("LOGYX-PAR-004",
                    "Otro operador cambió este parámetro mientras lo editabas. Recarga y vuelve a intentarlo", "value");
        }
        String previous = row == null ? defaultOf(key) : row.value;
        ensureCoherent(key, value);

        OffsetDateTime now = OffsetDateTime.now();
        UUID actor = tenant.profileId();
        if (row == null) {
            row = new SystemParameterEntity();
            row.key = key.key();
        }
        row.value = value;
        row.version = current + 1;
        row.reason = why;
        row.updatedBy = actor;
        row.updatedAt = now;
        row.persist();

        var h = new SystemParameterHistoryEntity();
        h.key = key.key();
        h.version = row.version;
        h.previousValue = previous;
        h.newValue = value;
        h.reason = why;
        h.changedBy = actor;
        h.changedByOrg = tenant.orgId();
        h.changedAt = now;
        h.persist();

        String detail = key.key() + ": " + previous + " → " + value + " (v" + row.version + ") · " + why;
        audit.record(actor, tenant.orgId(), "SYSTEM_PARAMETER_CHANGED", "system_parameter", null,
                detail.length() > 500 ? detail.substring(0, 499) + "…" : detail);

        invalidateAfterCommit();
        return new ParameterValue(key, value, defaultOf(key), row.version, why, actor, now);
    }

    /** Aviso (ámbar) siempre antes que incumplimiento (rojo). Compara con el otro valor ya vigente. */
    private void ensureCoherent(ParameterKey key, String newValue) {
        if (key != ParameterKey.INCIDENT_WARN_HOURS && key != ParameterKey.INCIDENT_BREACH_HOURS) return;
        ParameterKey other = key == ParameterKey.INCIDENT_WARN_HOURS ? ParameterKey.INCIDENT_BREACH_HOURS
                : ParameterKey.INCIDENT_WARN_HOURS;
        SystemParameterEntity o = SystemParameterEntity.findById(other.key());
        long otherHours = Long.parseLong(o == null ? defaultOf(other) : o.value);
        long mine = Long.parseLong(newValue);
        long warn = key == ParameterKey.INCIDENT_WARN_HOURS ? mine : otherHours;
        long breach = key == ParameterKey.INCIDENT_BREACH_HOURS ? mine : otherHours;
        if (warn >= breach) {
            throw BusinessException.conflict("LOGYX-PAR-003",
                    "El umbral de aviso (" + warn + " h) debe ser menor al de incumplimiento (" + breach + " h)", "value");
        }
    }

    private void invalidateAfterCommit() {
        cache = null;
        try {
            if (txRegistry.getTransactionStatus() == Status.STATUS_ACTIVE) {
                txRegistry.registerInterposedSynchronization(new Synchronization() {
                    @Override public void beforeCompletion() {}
                    @Override public void afterCompletion(int status) { cache = null; }
                });
            }
        } catch (RuntimeException ignored) {
            // sin sincronización disponible: el TTL pone al día la caché
        }
    }

    /** Vacía la caché (tests). */
    public void clearCache() { cache = null; }

    // ----- Historial (operador; RLS param_history_operator_read)

    /** Cambios más recientes primero, de un parámetro o de todos. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Paged<ParameterChange> history(ParameterKey key, PageRequest page) {
        String where = key == null ? "" : "WHERE h.param_key = ?1";
        var count = em.createNativeQuery("SELECT count(*) FROM system_parameter_history h " + where);
        var q = em.createNativeQuery("""
                SELECT h.id, h.param_key, h.version, h.previous_value, h.new_value, h.reason, h.changed_by,
                       p.full_name, p.email, h.changed_at
                  FROM system_parameter_history h LEFT JOIN profiles p ON p.id = h.changed_by
                """ + where + " ORDER BY h.changed_at DESC, h.id DESC LIMIT " + page.size() + " OFFSET " + page.offset());
        if (key != null) { count.setParameter(1, key.key()); q.setParameter(1, key.key()); }
        long total = ((Number) count.getSingleResult()).longValue();
        List<Object[]> rows = q.getResultList();
        List<ParameterChange> items = rows.stream().map(r -> new ParameterChange(((Number) r[0]).longValue(),
                ParameterKey.parse((String) r[1]).orElse(null), ((Number) r[2]).intValue(), (String) r[3],
                (String) r[4], (String) r[5], uuid(r[6]), (String) r[7], (String) r[8], odt(r[9]))).toList();
        return new Paged<>(items, page, total);
    }

    private static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static OffsetDateTime odt(Object v) {
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (v instanceof java.time.Instant i) return i.atOffset(java.time.ZoneOffset.UTC);
        return null;
    }
}
