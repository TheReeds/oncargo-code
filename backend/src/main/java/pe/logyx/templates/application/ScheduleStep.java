package pe.logyx.templates.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.PublishRequestUseCase;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.templates.application.event.TemplateRunRecorded;
import pe.logyx.templates.application.port.out.RequestScheduleRepository;
import pe.logyx.templates.application.port.out.RequestTemplateRepository;
import pe.logyx.templates.application.port.out.ScheduleRunRepository;
import pe.logyx.templates.domain.RecurrencePolicy;
import pe.logyx.templates.domain.model.RequestSchedule;
import pe.logyx.templates.domain.model.RequestTemplate;
import pe.logyx.templates.domain.model.ScheduleMode;
import pe.logyx.templates.domain.model.ScheduleRun;
import pe.logyx.templates.domain.model.ScheduleRunStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un paso de la programación bajo el tenant de la organización (ADR-0013): {@link #attempt} ejecuta la ocurrencia que
 * toca y {@link #recordFailure} deja constancia de un fallo en otra transacción (la del intento se revirtió).
 * Cada método es su propia transacción; los llama {@link ScheduledTemplates} dentro de {@code SyntheticTenant}.
 */
@ApplicationScoped
public class ScheduleStep {

    @Inject RequestScheduleRepository schedules;
    @Inject RequestTemplateRepository templates;
    @Inject ScheduleRunRepository runs;
    @Inject TemplateService templateService;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<TemplateRunRecorded> recorded;
    /** 2.14: feriados y paros editables por el operador (antes la lista fija PeruHolidays). */
    @Inject pe.logyx.catalog.application.CalendarService calendar;

    /**
     * Ejecuta la ocurrencia de {@code nextPickupDate} si la programación ya toca: la salta (feriado o fecha pasada),
     * deja un borrador o publica. Si publicar lanza, la transacción se revierte y el llamador usa {@link #recordFailure}.
     */
    @Transactional
    public void attempt(UUID scheduleId) {
        schedules.lock(scheduleId);
        RequestSchedule s = schedules.findById(scheduleId).orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        if (s == null || !s.isDue(now)) return;                                   // otra instancia ya la ejecutó
        RequestTemplate t = templates.findById(s.templateId()).orElseThrow();
        if (t.isArchived()) {
            s.pause();
            schedules.save(s);
            return;
        }
        LocalDate pickup = s.nextPickupDate();
        LocalDate today = LocalDate.now(RecurrencePolicy.LIMA);
        if (runs.exists(s.id(), pickup)) {                                        // ya registrada: solo avanza
            s.advance(true, now);
            schedules.save(s);
            return;
        }
        if (pickup.isBefore(today)) {
            skip(s, pickup, now, "La fecha de recojo ya pasó");
            return;
        }
        if (s.config().skipHolidays() && calendar.skipsRecurrence(pickup, t.data().originCity())) {
            skip(s, pickup, now, "Feriado: no se publicó");
            return;
        }
        if (s.config().mode() == ScheduleMode.DRAFT) {
            runs.save(ScheduleRun.of(s, pickup, ScheduleRunStatus.DRAFT, null, null, null));
            s.advance(true, now);
            schedules.save(s);
            fire(s, t, "DRAFT", pickup, null, null, null, false);
            return;
        }
        PublishRequestUseCase.Published out = templateService.publishFrom(t, pickup,
                "sch:" + s.id() + ":" + pickup, true);
        ShipmentRequest r = out.summary().request();
        runs.save(ScheduleRun.of(s, pickup, ScheduleRunStatus.PUBLISHED, r.id(), null, null));
        s.advance(true, now);
        schedules.save(s);
        fire(s, t, "PUBLISHED", pickup, r.id(), r.code(), null, false);
    }

    /** Registra el fallo de la ocurrencia que tocaba (sin reintentar) y pausa la programación tras varios seguidos. */
    @Transactional
    public void recordFailure(UUID scheduleId, RuntimeException cause) {
        schedules.lock(scheduleId);
        RequestSchedule s = schedules.findById(scheduleId).orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        if (s == null || !s.isDue(now)) return;
        RequestTemplate t = templates.findById(s.templateId()).orElseThrow();
        LocalDate pickup = s.nextPickupDate();
        String code = cause instanceof BusinessException b ? b.code() : "LOGYX-GEN-500";
        String message = cause instanceof BusinessException ? cause.getMessage() : "Error inesperado al publicar";
        if (!runs.exists(s.id(), pickup)) {
            runs.save(ScheduleRun.of(s, pickup, ScheduleRunStatus.FAILED, null, code, message));
        }
        s.advance(false, now);
        schedules.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_RUN_FAILED", "request_template", t.id(),
                code + " recojo=" + pickup);
        fire(s, t, "FAILED", pickup, null, null, message, s.isPaused());
    }

    private void skip(RequestSchedule s, LocalDate pickup, OffsetDateTime now, String why) {
        runs.save(ScheduleRun.of(s, pickup, ScheduleRunStatus.SKIPPED, null, null, why));
        s.advance(true, now);
        schedules.save(s);
    }

    private void fire(RequestSchedule s, RequestTemplate t, String kind, LocalDate pickup, UUID requestId, String code,
                      String message, boolean paused) {
        var d = t.data();
        recorded.fire(new TemplateRunRecorded(s.organizationId(), t.createdBy(), t.id(), d.name(), kind, pickup,
                requestId, code, d.originCity().label + " → " + d.destinationCity().label, message, paused));
    }
}
