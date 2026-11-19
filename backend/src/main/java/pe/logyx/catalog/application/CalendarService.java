package pe.logyx.catalog.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.catalog.application.port.out.CalendarDayRepository;
import pe.logyx.catalog.domain.City;
import pe.logyx.catalog.domain.HolidayCalendar;
import pe.logyx.catalog.domain.model.CalendarDay;
import pe.logyx.catalog.domain.model.CalendarDayKind;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calendario de feriados y paros (roadmap 2.14, V24). Lo lee cualquiera (banner, aviso al elegir fecha, plantillas
 * recurrentes); lo edita solo el operador con motivo y auditoría.
 *
 * <p>Lectura por {@code SystemTx} con caché corta (justificación: lo consultan jobs sin usuario —plantillas
 * recurrentes— y es un dato de plataforma; la política RLS {@code calendar_read} también lo deja leer a cualquiera).
 */
@ApplicationScoped
public class CalendarService {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Consulta de hasta un año y medio de calendario por petición. */
    static final int MAX_RANGE_DAYS = 550;

    /** Alta de un día. {@code cities} vacío = nacional. */
    public record NewCalendarDay(LocalDate day, CalendarDayKind kind, String name, Set<City> cities,
                                 boolean skipRecurrence, String note) {}

    @Inject CalendarDayRepository days;
    @Inject SystemTx systemTx;
    @Inject TenantContext tenant;
    @Inject AuditService audit;
    @Inject TransactionSynchronizationRegistry txRegistry;

    @ConfigProperty(name = "logyx.parameters.cache-ttl", defaultValue = "PT10S") Duration cacheTtl;

    private record Cached(List<CalendarDay> all, long loadedAt) {}

    private volatile Cached cache;

    /** Días entre {@code from} y {@code to} (inclusive), en orden. {@code LOGYX-VAL-001} si el rango es inválido. */
    public List<CalendarDay> between(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "Indica un rango de fechas válido (hasta " + MAX_RANGE_DAYS + " días)", "to");
        }
        return snapshot().stream().filter(d -> !d.day().isBefore(from) && !d.day().isAfter(to)).toList();
    }

    /** Lo que cae en esa fecha para esa ciudad (o los nacionales si {@code city} es nulo). */
    public List<CalendarDay> on(LocalDate date, City city) {
        return new HolidayCalendar(snapshot()).on(date, city);
    }

    /** 2.1: la carga recurrente con origen en {@code city} no se publica para recoger ese día. */
    public boolean skipsRecurrence(LocalDate date, City city) {
        return new HolidayCalendar(snapshot()).skipsRecurrence(date, city);
    }

    private List<CalendarDay> snapshot() {
        Cached c = cache;
        long now = System.currentTimeMillis();
        if (c != null && now - c.loadedAt() < cacheTtl.toMillis()) return c.all();
        // SystemTx: dato de plataforma que leen también los jobs sin usuario.
        List<CalendarDay> all = systemTx.call(() -> days.all());
        cache = new Cached(all, now);
        return all;
    }

    /**
     * Agrega un feriado, paro o aviso (operador). {@code LOGYX-CAL-001} fecha pasada o a más de 3 años,
     * {@code CAL-002} (409) ya existe ese día con ese nombre, {@code VAL-001} nombre vacío. RN07 en nombre y nota.
     */
    @Transactional
    public CalendarDay add(NewCalendarDay req) {
        requireOperator();
        LocalDate today = LocalDate.now(LIMA);
        if (req.day() == null || req.day().isBefore(today) || req.day().isAfter(today.plusYears(3))) {
            throw BusinessException.badRequest("LOGYX-CAL-001", "Elige una fecha desde hoy y hasta 3 años adelante", "day");
        }
        if (req.kind() == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el tipo", "kind");
        String name = ContactInfoPolicy.clean(req.name(), "name");
        if (name == null || name.isBlank() || name.length() > 120) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el nombre (hasta 120 caracteres)", "name");
        }
        String note = ContactInfoPolicy.clean(req.note(), "note");
        if (note != null && note.length() > 300) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La nota admite hasta 300 caracteres", "note");
        }
        if (days.exists(req.day(), req.kind().name(), name)) {
            throw BusinessException.conflict("LOGYX-CAL-002", "Ese día ya está en el calendario con ese nombre", "name");
        }
        CalendarDay d = new CalendarDay(UUID.randomUUID(), req.day(), req.kind(), name, req.cities(),
                req.skipRecurrence(), note == null || note.isBlank() ? null : note, tenant.profileId(),
                OffsetDateTime.now());
        days.insert(d);
        audit.record(tenant.profileId(), tenant.orgId(), "CALENDAR_DAY_ADDED", "calendar_day", d.id(),
                d.day() + " " + d.kind() + " " + d.name() + (d.national() ? "" : " " + d.cities()));
        invalidateAfterCommit();
        return d;
    }

    /** Quita un día (operador) con motivo. 404 si no existe. */
    @Transactional
    public void remove(UUID id, String reason) {
        requireOperator();
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el motivo", "reason");
        CalendarDay d = days.find(id).orElseThrow(() -> BusinessException.notFound("Día del calendario"));
        days.delete(id);
        String detail = d.day() + " " + d.kind() + " " + d.name() + " · " + why;
        audit.record(tenant.profileId(), tenant.orgId(), "CALENDAR_DAY_REMOVED", "calendar_day", id,
                detail.length() > 500 ? detail.substring(0, 499) + "…" : detail);
        invalidateAfterCommit();
    }

    private void requireOperator() {
        if (tenant.orgType() != OrgType.OPERATOR) {
            throw BusinessException.forbidden("Solo el operador de OnCargo edita el calendario");
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
            // el TTL pone al día la caché
        }
    }

    /** Vacía la caché (tests). */
    public void clearCache() { cache = null; }
}
