package pe.logyx.templates.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.application.PublishRequestCommand;
import pe.logyx.marketplace.application.PublishRequestUseCase;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.templates.application.port.out.RequestScheduleRepository;
import pe.logyx.templates.application.port.out.RequestTemplateRepository;
import pe.logyx.templates.application.port.out.ScheduleRunRepository;
import pe.logyx.templates.domain.RecurrencePolicy;
import pe.logyx.templates.domain.model.RequestSchedule;
import pe.logyx.templates.domain.model.RequestTemplate;
import pe.logyx.templates.domain.model.ScheduleRun;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plantillas de solicitud y su programación (roadmap 2.1). La organización sale del JWT; la RLS protege las tablas por
 * duplicado. Publicar desde una plantilla pasa por {@link PublishRequestUseCase}: RN01, RN03, RN07 y la idempotencia se
 * evalúan en cada publicación. VIEWER solo lee (lo aplica {@code AccountAccessFilter}).
 */
@ApplicationScoped
@Transactional
public class TemplateService {

    public static final int MAX_LIST = 200;
    public static final int RUNS_PER_TEMPLATE = 5;

    /** Una plantilla con su programación (si tiene) y sus últimas ocurrencias. */
    public record Overview(RequestTemplate template, RequestSchedule schedule, List<ScheduleRun> recentRuns) {}

    /** Un borrador por confirmar y la plantilla de la que sale. */
    public record Draft(ScheduleRun run, RequestTemplate template) {}

    @Inject RequestTemplateRepository templates;
    @Inject RequestScheduleRepository schedules;
    @Inject ScheduleRunRepository runs;
    @Inject PublishRequestUseCase publishUseCase;
    @Inject MarketplaceQueries marketplace;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    // ----- Plantillas

    public List<Overview> list(boolean includeArchived) {
        List<RequestTemplate> all = templates.list(tenant.orgId(), includeArchived, MAX_LIST);
        return overviews(all);
    }

    public Overview get(UUID id) {
        return overviews(List.of(owned(id))).getFirst();
    }

    public RequestTemplate create(RequestTemplate.Data data) {
        ensureNameFree(data.name(), null);
        RequestTemplate t = RequestTemplate.create(tenant.orgId(), tenant.profileId(), data);
        templates.save(t);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_CREATED", "request_template", t.id(), "nombre=" + t.data().name());
        return t;
    }

    /** «Guardar como plantilla» desde una solicitud ya publicada: copia su carga, contactos y direcciones. */
    public RequestTemplate createFromRequest(UUID requestId, String name, boolean invitePreferred) {
        ShipmentRequest r = marketplace.detail(requestId).request();
        if (!r.isOwnedBy(tenant.orgId())) throw BusinessException.notFound("Solicitud");
        var c = r.cargo();
        return create(new RequestTemplate.Data(name, c.originCity(), c.originAddress(), c.destinationCity(),
                c.destinationAddress(), c.cargoType(), c.cargoDescription(), c.weightKg(), c.volumeM3(), r.details(),
                null, null, invitePreferred));
    }

    public RequestTemplate update(UUID id, RequestTemplate.Data data) {
        RequestTemplate t = owned(id);
        ensureNameFree(data.name(), t.id());
        t.update(data);
        templates.save(t);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_UPDATED", "request_template", t.id(), null);
        return t;
    }

    public RequestTemplate archive(UUID id) {
        RequestTemplate t = owned(id);
        t.archive();
        templates.save(t);
        schedules.findByTemplate(t.id()).ifPresent(s -> { s.pause(); schedules.save(s); });   // archivada no publica sola
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_ARCHIVED", "request_template", t.id(), null);
        return t;
    }

    public RequestTemplate restore(UUID id) {
        RequestTemplate t = owned(id);
        ensureNameFree(t.data().name(), t.id());
        t.restore();
        templates.save(t);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_RESTORED", "request_template", t.id(), null);
        return t;
    }

    // ----- Publicar

    /** «Publicar ahora»: publica una solicitud con la fecha de recojo dada. Misma protección contra doble publicación. */
    public PublishRequestUseCase.Published publishNow(UUID id, LocalDate pickupDate, String idempotencyKey) {
        RequestTemplate t = owned(id);
        t.ensureActive();
        PublishRequestUseCase.Published out = publishFrom(t, pickupDate, idempotencyKey, false);
        return out;
    }

    /** Publica el borrador generado por la programación (confirmación de la persona). */
    public PublishRequestUseCase.Published publishDraft(UUID runId) {
        ScheduleRun run = ownedRun(runId);
        if (!run.isDraft()) throw BusinessException.conflict("LOGYX-TPL-004", "Ese borrador ya se resolvió");
        RequestTemplate t = owned(run.templateId());
        t.ensureActive();
        PublishRequestUseCase.Published out = publishFrom(t, run.pickupDate(), "draft:" + run.id(), false);
        run.published(out.summary().request().id());
        runs.save(run);
        return out;
    }

    public ScheduleRun discardDraft(UUID runId) {
        ScheduleRun run = ownedRun(runId);
        run.discard();
        runs.save(run);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_DRAFT_DISCARDED", "request_template", run.templateId(),
                "recojo=" + run.pickupDate());
        return run;
    }

    public List<Draft> drafts() {
        List<ScheduleRun> drafts = runs.listDrafts(tenant.orgId());
        Map<UUID, RequestTemplate> byId = byId(drafts.stream().map(ScheduleRun::templateId).toList());
        return drafts.stream().filter(d -> byId.containsKey(d.templateId()))
                .map(d -> new Draft(d, byId.get(d.templateId()))).toList();
    }

    /**
     * Publica con el caso de uso de marketplace y deja la huella: auditoría «creada por plantilla X» y la fecha de la
     * última publicación. {@code automatic} distingue lo que hizo la programación de lo que hizo una persona.
     */
    public PublishRequestUseCase.Published publishFrom(RequestTemplate t, LocalDate pickupDate, String idempotencyKey,
                                                boolean automatic) {
        LocalDate today = LocalDate.now(RecurrencePolicy.LIMA);
        if (pickupDate == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica la fecha de recojo", "pickupDate");
        }
        if (pickupDate.isBefore(today)) {
            throw BusinessException.badRequest("LOGYX-REQ-003", "La fecha de recojo ya pasó: indica una nueva fecha",
                    "pickupDate");
        }
        var d = t.data();
        PublishRequestUseCase.Published out = publishUseCase.publishOnce(new PublishRequestCommand(
                d.originCity().name(), d.originAddress(), d.destinationCity().name(), d.destinationAddress(),
                d.cargoType(), d.cargoDescription(), d.weightKg(), d.volumeM3(), pickupDate, d.details(),
                d.originPlaceId(), d.destinationPlaceId(), idempotencyKey, d.invitePreferred()));
        if (!out.replayed()) {
            t.markPublished(java.time.OffsetDateTime.now());
            templates.save(t);
            ShipmentRequest r = out.summary().request();
            audit.record(tenant.profileId(), tenant.orgId(), "REQUEST_PUBLISHED_BY_TEMPLATE", "shipment_request", r.id(),
                    "plantilla=" + t.data().name() + " id=" + t.id() + (automatic ? " automatica=si" : " automatica=no"));
        }
        return out;
    }

    // ----- Programación

    public RequestSchedule setSchedule(UUID templateId, RequestSchedule.Config config) {
        RequestTemplate t = owned(templateId);
        t.ensureActive();
        LocalDate today = LocalDate.now(RecurrencePolicy.LIMA);
        RequestSchedule s = schedules.findByTemplate(t.id()).map(existing -> {
            existing.restart(config, today);
            return existing;
        }).orElseGet(() -> RequestSchedule.create(tenant.orgId(), t.id(), config, today));
        schedules.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_SCHEDULED", "request_template", t.id(),
                config.frequency() + " " + config.pickupWeekday() + " anticipacion=" + config.leadDays()
                        + " modo=" + config.mode());
        return s;
    }

    public RequestSchedule pause(UUID templateId) {
        RequestSchedule s = scheduleOf(templateId);
        s.pause();
        schedules.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_PAUSED", "request_template", templateId, null);
        return s;
    }

    public RequestSchedule resume(UUID templateId) {
        RequestTemplate t = owned(templateId);
        t.ensureActive();
        RequestSchedule s = scheduleOf(templateId);
        s.resume(LocalDate.now(RecurrencePolicy.LIMA));
        schedules.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_RESUMED", "request_template", templateId, null);
        return s;
    }

    /** Quita la programación (la plantilla queda; el historial de ocurrencias se conserva). */
    public void removeSchedule(UUID templateId) {
        owned(templateId);
        schedules.findByTemplate(templateId).ifPresent(s -> {
            schedules.delete(s.id());
            audit.record(tenant.profileId(), tenant.orgId(), "TEMPLATE_UNSCHEDULED", "request_template", templateId, null);
        });
    }

    // ----- helpers

    private RequestTemplate owned(UUID id) {
        return templates.findById(id).filter(t -> t.organizationId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Plantilla"));
    }

    private ScheduleRun ownedRun(UUID id) {
        return runs.findById(id).filter(r -> r.organizationId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Borrador"));
    }

    private RequestSchedule scheduleOf(UUID templateId) {
        owned(templateId);
        return schedules.findByTemplate(templateId)
                .orElseThrow(() -> BusinessException.conflict("LOGYX-TPL-005", "La plantilla no tiene programación"));
    }

    private void ensureNameFree(String name, UUID exceptId) {
        if (name != null && templates.activeNameTaken(tenant.orgId(), name.trim(), exceptId)) {
            throw BusinessException.conflict("LOGYX-TPL-001", "Ya tienes una plantilla con ese nombre", "name");
        }
    }

    private Map<UUID, RequestTemplate> byId(Collection<UUID> ids) {
        return ids.stream().distinct().map(templates::findById).flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(RequestTemplate::id, Function.identity()));
    }

    private List<Overview> overviews(List<RequestTemplate> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(RequestTemplate::id).toList();
        Map<UUID, RequestSchedule> sch = schedules.listByTemplates(ids).stream()
                .collect(Collectors.toMap(RequestSchedule::templateId, Function.identity()));
        Map<UUID, List<ScheduleRun>> recent = runs.listRecent(ids, RUNS_PER_TEMPLATE).stream()
                .collect(Collectors.groupingBy(ScheduleRun::templateId));
        return list.stream().map(t -> new Overview(t, sch.get(t.id()), recent.getOrDefault(t.id(), List.of()))).toList();
    }
}
