package pe.logyx.templates.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestResponse;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.idempotency.IdempotencyKeys;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.templates.application.TemplateService;
import pe.logyx.templates.infrastructure.web.TemplateDtos.*;

import java.util.List;
import java.util.UUID;

/** Plantillas de solicitud y cargas recurrentes de la PYME (ADMIN y OPS escriben; VIEWER solo lee). */
@Path("/api/v1")
public class TemplatesResource {

    @Inject TemplateService templates;

    @GET
    @Path("/request-templates")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Plantillas de la organización con su programación y últimas ocurrencias: la publicada más "
            + "recientemente primero")
    public ApiResponse<List<TemplateView>> list(
            @Parameter(description = "Incluye las archivadas (por omisión no).")
            @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived) {
        return ApiResponse.of(templates.list(includeArchived).stream().map(TemplateView::of).toList());
    }

    @POST
    @Path("/request-templates")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    @Operation(summary = "Guarda una plantilla (RN07 se valida aquí y en cada publicación). Nombre único entre las activas: LOGYX-TPL-001")
    public ApiResponse<TemplateView> create(@Valid TemplateRequest req) {
        return ApiResponse.of(TemplateView.of(templates.create(req.toData())));
    }

    @POST
    @Path("/request-templates/from-request/{requestId}")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    @Operation(summary = "Guarda como plantilla una solicitud tuya (carga, contactos y ventanas incluidos)")
    public ApiResponse<TemplateView> fromRequest(@PathParam("requestId") UUID requestId, @Valid TemplateFromRequest req) {
        return ApiResponse.of(TemplateView.of(templates.createFromRequest(requestId, req.name(),
                Boolean.TRUE.equals(req.invitePreferred()))));
    }

    @GET
    @Path("/request-templates/drafts")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Borradores que dejó la programación (modo DRAFT) y esperan confirmación")
    public ApiResponse<List<TemplateDraftView>> drafts() {
        return ApiResponse.of(templates.drafts().stream().map(TemplateDraftView::of).toList());
    }

    @POST
    @Path("/request-templates/runs/{runId}/publish")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    @Operation(summary = "Publica un borrador de la programación con su fecha de recojo (RN01, RN03 y RN07 al publicar)")
    public ApiResponse<PublishedFromTemplate> publishDraft(@PathParam("runId") UUID runId) {
        return ApiResponse.of(PublishedFromTemplate.of(templates.publishDraft(runId)));
    }

    @POST
    @Path("/request-templates/runs/{runId}/discard")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Descarta un borrador de la programación")
    public ApiResponse<TemplateRunView> discardDraft(@PathParam("runId") UUID runId) {
        return ApiResponse.of(TemplateRunView.of(templates.discardDraft(runId)));
    }

    @GET
    @Path("/request-templates/{id}")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<TemplateView> get(@PathParam("id") UUID id) {
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }

    @PUT
    @Path("/request-templates/{id}")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Reemplaza los campos de una plantilla activa (archivada: LOGYX-TPL-002)")
    public ApiResponse<TemplateView> update(@PathParam("id") UUID id, @Valid TemplateRequest req) {
        templates.update(id, req.toData());
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }

    @POST
    @Path("/request-templates/{id}/archive")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Archiva la plantilla (no se borra) y pausa su programación")
    public ApiResponse<TemplateView> archive(@PathParam("id") UUID id) {
        templates.archive(id);
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }

    @POST
    @Path("/request-templates/{id}/restore")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<TemplateView> restore(@PathParam("id") UUID id) {
        templates.restore(id);
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }

    @POST
    @Path("/request-templates/{id}/publish")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Publicar ahora: crea una solicitud con la fecha de recojo dada. Con Idempotency-Key un reintento "
            + "devuelve la ya creada (Idempotent-Replayed: true)")
    public RestResponse<ApiResponse<PublishedFromTemplate>> publish(
            @PathParam("id") UUID id,
            @Parameter(description = "Clave de idempotencia (UUID recomendado), válida 24 h por organización.")
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid TemplatePublishNow req) {
        var out = templates.publishNow(id, req.pickupDate(), IdempotencyKeys.validate(idempotencyKey));
        var builder = RestResponse.ResponseBuilder.create(RestResponse.Status.CREATED,
                ApiResponse.of(PublishedFromTemplate.of(out)));
        if (out.replayed()) builder.header("Idempotent-Replayed", "true");
        return builder.build();
    }

    // ----- Programación

    @PUT
    @Path("/request-templates/{id}/schedule")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Programa la plantilla (semanal o quincenal, día de recojo, anticipación, fin, feriados y modo). "
            + "Reemplaza la programación anterior y la reanuda")
    public ApiResponse<TemplateView> schedule(@PathParam("id") UUID id, @Valid TemplateScheduleRequest req) {
        templates.setSchedule(id, req.toConfig());
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }

    @POST
    @Path("/request-templates/{id}/schedule/pause")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<TemplateView> pause(@PathParam("id") UUID id) {
        templates.pause(id);
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }

    @POST
    @Path("/request-templates/{id}/schedule/resume")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<TemplateView> resume(@PathParam("id") UUID id) {
        templates.resume(id);
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }

    @DELETE
    @Path("/request-templates/{id}/schedule")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Quita la programación; la plantilla y el historial quedan")
    public ApiResponse<TemplateView> unschedule(@PathParam("id") UUID id) {
        templates.removeSchedule(id);
        return ApiResponse.of(TemplateView.of(templates.get(id)));
    }
}
