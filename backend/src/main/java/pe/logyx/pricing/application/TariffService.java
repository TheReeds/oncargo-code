package pe.logyx.pricing.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.pricing.application.port.out.TariffRepository;
import pe.logyx.pricing.application.port.out.TariffRepository.TariffRecord;
import pe.logyx.pricing.domain.model.Tariff;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Tarifario versionado con vigencia (roadmap 2.14, V24). Lectura para cualquier módulo con caché corta (como
 * {@code SystemParameters}): {@link #current()} es la versión de mayor número con {@code effectiveFrom <= ahora} y sin
 * anular. Escritura solo del operador: una versión nueva (vigente ya o programada) con motivo, o anular una programada.
 *
 * <p>Lee por {@code SystemTx}: justificación, el cotizador público y los jobs (plantillas recurrentes) cotizan sin
 * usuario, y el tarifario es un dato de la plataforma, no de una organización. Una escritura vacía la caché de esta
 * instancia al confirmar; las demás se ponen al día al vencer el TTL ({@code logyx.parameters.cache-ttl}, 10 s).
 */
@ApplicationScoped
public class TariffService {

    /** Una versión programada se puede fijar hasta un año adelante. */
    static final Duration MAX_SCHEDULE_AHEAD = Duration.ofDays(366);
    /** Tolerancia de reloj: «desde ahora» enviado por la web puede llegar unos minutos tarde. */
    static final Duration PAST_TOLERANCE = Duration.ofMinutes(10);

    public enum State { CURRENT, SCHEDULED, PAST, CANCELLED }

    /** Una versión y su estado respecto de ahora. */
    public record TariffVersionInfo(TariffRecord record, State state) {}

    /** Valores de una versión nueva (sin número: lo asigna el servicio). */
    public record NewTariff(java.math.BigDecimal baseFee, java.math.BigDecimal fuelPerKm,
                            java.math.BigDecimal ratePerKmTon, java.math.BigDecimal tollEveryKm,
                            java.math.BigDecimal tollPrice, java.math.BigDecimal handlingRate,
                            java.util.Map<pe.logyx.catalog.domain.CargoType, java.math.BigDecimal> cargoMultipliers,
                            OffsetDateTime effectiveFrom, String reason, Integer expectedLatestVersion) {}

    @Inject TariffRepository tariffs;
    @Inject SystemTx systemTx;
    @Inject TenantContext tenant;
    @Inject AuditService audit;
    @Inject TransactionSynchronizationRegistry txRegistry;

    @ConfigProperty(name = "logyx.parameters.cache-ttl", defaultValue = "PT10S") Duration cacheTtl;

    private record Cached(List<TariffRecord> all, long loadedAt) {}

    private volatile Cached cache;

    /** Tarifa vigente ahora. */
    public Tariff current() {
        return currentAt(OffsetDateTime.now());
    }

    /** Tarifa vigente en un instante (la de mayor versión ya en vigor y no anulada). */
    public Tariff currentAt(OffsetDateTime at) {
        return snapshot().stream().filter(r -> !r.cancelled() && !r.tariff().effectiveFrom().isAfter(at))
                .map(TariffRecord::tariff).findFirst().orElse(Tariff.STANDARD);
    }

    /** Todas las versiones con su estado, la más nueva primero. */
    public List<TariffVersionInfo> versions() {
        OffsetDateTime now = OffsetDateTime.now();
        int current = currentAt(now).version();
        return snapshot().stream().map(r -> new TariffVersionInfo(r, r.cancelled() ? State.CANCELLED
                : r.tariff().version() == current ? State.CURRENT
                : r.tariff().effectiveFrom().isAfter(now) ? State.SCHEDULED : State.PAST)).toList();
    }

    public Optional<TariffVersionInfo> version(int number) {
        return versions().stream().filter(v -> v.record().tariff().version() == number).findFirst();
    }

    private List<TariffRecord> snapshot() {
        Cached c = cache;
        long now = System.currentTimeMillis();
        if (c != null && now - c.loadedAt() < cacheTtl.toMillis()) return c.all();
        // SystemTx: dato de plataforma, también sin usuario (cotizador público, jobs).
        List<TariffRecord> all = systemTx.call(() -> tariffs.all());
        cache = new Cached(all, now);
        return all;
    }

    /**
     * Crea una versión (operador). {@code effectiveFrom} nulo = desde ya. {@code LOGYX-TRF-001} valores fuera de
     * rango, {@code TRF-002} sin motivo, {@code TRF-003} vigencia en el pasado o a más de un año,
     * {@code TRF-005} (409) otro operador creó una versión mientras editabas ({@code expectedLatestVersion}).
     */
    @Transactional
    public TariffVersionInfo create(NewTariff req) {
        requireOperator();
        String why = req.reason() == null ? "" : req.reason().trim();
        if (why.length() < 3) throw BusinessException.badRequest("LOGYX-TRF-002", "Indica el motivo del cambio", "reason");
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from = req.effectiveFrom() == null ? now : req.effectiveFrom();
        if (from.isBefore(now.minus(PAST_TOLERANCE))) {
            throw BusinessException.badRequest("LOGYX-TRF-003", "La vigencia no puede empezar en el pasado", "effectiveFrom");
        }
        if (from.isAfter(now.plus(MAX_SCHEDULE_AHEAD))) {
            throw BusinessException.badRequest("LOGYX-TRF-003", "La vigencia puede programarse hasta un año adelante",
                    "effectiveFrom");
        }
        if (from.isBefore(now)) from = now;
        int latest = tariffs.lockAndMaxVersion();
        if (req.expectedLatestVersion() != null && req.expectedLatestVersion() != latest) {
            throw BusinessException.conflict("LOGYX-TRF-005",
                    "Otro operador cambió el tarifario mientras lo editabas. Recarga y vuelve a intentarlo", "reason");
        }
        Tariff t = new Tariff(latest + 1, req.baseFee(), req.fuelPerKm(), req.ratePerKmTon(), req.tollEveryKm(),
                req.tollPrice(), req.handlingRate(), req.cargoMultipliers(), from);
        t.validate();
        tariffs.insert(t, why, tenant.profileId());
        String detail = "v" + t.version() + " desde " + from + " · " + why;
        audit.record(tenant.profileId(), tenant.orgId(), "TARIFF_VERSION_CREATED", "tariff_version", null,
                detail.length() > 500 ? detail.substring(0, 499) + "…" : detail);
        invalidateAfterCommit();
        boolean scheduled = from.isAfter(now);
        return new TariffVersionInfo(new TariffRecord(t, why, tenant.profileId(), null, now, null),
                scheduled ? State.SCHEDULED : State.CURRENT);
    }

    /** Anula una versión programada que aún no rige ({@code LOGYX-TRF-004}, 409, si ya rige o está anulada). */
    @Transactional
    public void cancel(int version, String reason) {
        requireOperator();
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) throw BusinessException.badRequest("LOGYX-TRF-002", "Indica el motivo", "reason");
        tariffs.lockAndMaxVersion();
        if (!tariffs.cancelScheduled(version, tenant.profileId(), OffsetDateTime.now())) {
            throw BusinessException.conflict("LOGYX-TRF-004",
                    "Solo se anula una versión programada que todavía no rige");
        }
        audit.record(tenant.profileId(), tenant.orgId(), "TARIFF_VERSION_CANCELLED", "tariff_version", null,
                "v" + version + " · " + why);
        invalidateAfterCommit();
    }

    private void requireOperator() {
        if (tenant.orgType() != OrgType.OPERATOR) {
            throw BusinessException.forbidden("Solo el operador de OnCargo cambia el tarifario");
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
            // sin sincronización: el TTL pone al día la caché
        }
    }

    /** Vacía la caché (tests). */
    public void clearCache() { cache = null; }
}
