package pe.logyx.activity.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.activity.application.ActivityQueries;
import pe.logyx.activity.domain.model.ActivityEntry;
import pe.logyx.activity.domain.model.ActivitySubject;
import pe.logyx.activity.domain.model.Author;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Autoría y rastro de acciones de un recurso (roadmap v2 1.8). Solo lectura, bajo RLS. */
@Path("/api/v1/activity")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
public class ActivityResource {

    /** Persona de la propia organización (o cualquiera, para el operador). {@code role}: ADMIN, OPS, VIEWER… */
    public record ActivityPerson(String name, String role) {
        static ActivityPerson of(Author a) { return new ActivityPerson(a.name(), a.role()); }
    }

    /**
     * Una acción: {@code action} es el código de la auditoría ({@code REQUEST_PUBLISHED}, {@code BID_ACCEPTED}…, más
     * {@code BID_PLACED}). {@code actor} es null si la hizo otra organización (no ves a sus personas) o el sistema.
     */
    public record ActivityEntryView(OffsetDateTime at, String action, String entity, UUID entityId, ActivityPerson actor,
                                    String organizationName, boolean own) {
        static ActivityEntryView of(ActivityEntry e) {
            return new ActivityEntryView(e.at(), e.action(), e.entity(), e.entityId(),
                    e.actorName() == null ? null : new ActivityPerson(e.actorName(), e.actorRole()),
                    e.organizationName(), e.own());
        }
    }

    public record ActivityView(ActivityPerson createdBy, List<ActivityEntryView> entries) {}

    @Inject ActivityQueries queries;

    @GET
    @Operation(summary = "Quién creó un recurso y quién hizo cada acción",
            description = "Lectura de created_by y de la auditoría bajo RLS: cada organización ve sus propias acciones y "
                    + "a sus personas; el operador ve todo. Hasta 100 acciones, recientes primero. 404 si no ves el recurso.")
    public ApiResponse<ActivityView> activity(
            @Parameter(description = "REQUEST, BID, SHIPMENT, TRIP o INCIDENT.") @QueryParam("subject") @NotNull ActivitySubject subject,
            @Parameter(description = "Id del recurso.") @QueryParam("id") @NotNull UUID id) {
        var a = queries.of(subject, id);
        return ApiResponse.of(new ActivityView(a.createdBy().map(ActivityPerson::of).orElse(null),
                a.entries().stream().map(ActivityEntryView::of).toList()));
    }
}
