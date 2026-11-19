package pe.logyx.incident.infrastructure.web;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import pe.logyx.incident.domain.model.IncidentShipmentOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;

import pe.logyx.incident.domain.model.IncidentSeverity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class IncidentDtos {
    private IncidentDtos() {}

    /**
     * V23 (2.7), todo opcional salvo categoría y descripción: {@code severity} (por omisión la de la categoría),
     * {@code claimedAmount}, {@code attachmentIds} (documentos de este envío ya subidos, hasta 5), ubicación y hora del
     * dispositivo (botón «Emergencia»), {@code adjustmentId} (ajuste del envío relacionado, p. ej. en PAYMENT_DISPUTE).
     */
    public record ReportIncident(@NotNull IncidentCategory category,
                                 @NotBlank @Size(max = 2000) String description,
                                 IncidentSeverity severity,
                                 @jakarta.validation.constraints.DecimalMin("0.01") BigDecimal claimedAmount,
                                 @Size(max = 5) List<UUID> attachmentIds,
                                 @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") BigDecimal latitude,
                                 @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") BigDecimal longitude,
                                 OffsetDateTime occurredAt, UUID adjustmentId) {
        public ReportIncident(IncidentCategory category, String description) {
            this(category, description, null, null, null, null, null, null, null);
        }
    }

    /** {@code internal} (V23): nota interna del operador, que las partes no ven. */
    public record AddIncidentComment(@NotBlank @Size(max = 2000) String body, Boolean internal) {
        public AddIncidentComment(String body) { this(body, null); }
    }

    /** Operador (2.7): reabrir con motivo. */
    public record ReopenIncident(@NotBlank(message = "Indica el motivo") @Size(max = 2000) String reason) {}

    /**
     * Operador (2.7): {@code assigneeProfileId} null = me la asigno; {@code unassign} = sin responsable.
     */
    public record AssignIncident(UUID assigneeProfileId, Boolean unassign) {}

    /** Documento adjunto en el alta. */
    public record IncidentAttachmentView(UUID documentId, String fileName, String type, String contentType) {}

    /**
     * {@code resolutionNotes}: obligatorio al resolver o cerrar una incidencia activa (motivo, F27).
     * {@code shipmentOutcome}: obligatorio en esos mismos casos (estado final del envío, RF55).
     */
    public record ChangeIncidentStatus(@NotNull IncidentStatus status,
                                       @Size(max = 2000) String resolutionNotes,
                                       IncidentShipmentOutcome shipmentOutcome) {}

    public record IncidentCommentView(UUID id, UUID authorOrgId, String authorName, String body,
                                      OffsetDateTime createdAt,
                                      /** V23: nota interna del operador (solo la ve el operador). */
                                      boolean internal) {
        static IncidentCommentView of(IncidentComment c, IncidentSummary s) {
            return new IncidentCommentView(c.id(), c.authorOrgId(), s.name(c.authorOrgId()), c.body(), c.createdAt(),
                    c.internal());
        }
    }

    /** {@code active} = OPEN o IN_REVIEW: el envío está congelado (RN11). {@code comments} solo en el detalle. */
    public record IncidentView(UUID id, UUID shipmentId, UUID requesterOrgId, String requesterName,
                               UUID carrierOrgId, String carrierName, UUID reportedByOrgId,
                               IncidentCategory category, String description, IncidentStatus status,
                               boolean active, String resolutionNotes, OffsetDateTime createdAt,
                               OffsetDateTime updatedAt, OffsetDateTime resolvedAt,
                               List<IncidentCommentView> comments,
                               /** Estado final del envío que fijó el operador al resolverla (RF55). */
                               IncidentShipmentOutcome shipmentOutcome,
                               /** V23: severidad (fija el SLA). */
                               IncidentSeverity severity,
                               /** V23: true = congela el envío mientras está activa (RN11); false = informativa. */
                               boolean blocking,
                               /** V23: el envío está detenido por esta incidencia (activa y blocking). */
                               boolean holdsShipment,
                               BigDecimal claimedAmount,
                               /** V23: valor declarado de la carga en la solicitud (solo en el detalle). */
                               BigDecimal declaredValue,
                               /** V23: vencimiento del SLA y si ya venció estando activa. */
                               OffsetDateTime dueAt, boolean slaBreached,
                               /** V23: responsable del operador (el nombre solo lo ve el operador). */
                               UUID assigneeProfileId, String assigneeName,
                               OffsetDateTime withdrawnAt, OffsetDateTime reopenedAt, int reopenCount,
                               /** V23: ubicación y hora del dispositivo del reporte (botón «Emergencia»). */
                               BigDecimal latitude, BigDecimal longitude, OffsetDateTime occurredAt,
                               UUID adjustmentId,
                               /** V23: adjuntos del alta (solo en el detalle). */
                               List<IncidentAttachmentView> attachments,
                               /** V23: quien la reportó puede retirarla ahora (informativa, activa, de su organización). */
                               boolean canWithdraw) {
        static IncidentView of(IncidentSummary s) {
            Incident i = s.incident();
            var tenant = io.quarkus.arc.Arc.container().instance(pe.logyx.shared.tenancy.TenantContext.class).get();
            boolean mine = tenant.isAuthenticated() && i.reportedByOrgId().equals(tenant.orgId());
            var loc = i.location();
            return new IncidentView(i.id(), i.shipmentId(), i.requesterOrgId(), s.name(i.requesterOrgId()),
                    i.carrierOrgId(), s.name(i.carrierOrgId()), i.reportedByOrgId(), i.category(), i.description(),
                    i.status(), i.isActive(), i.resolutionNotes(), i.createdAt(), i.updatedAt(), i.resolvedAt(),
                    s.comments().stream().map(c -> IncidentCommentView.of(c, s)).toList(), i.shipmentOutcome(),
                    i.severity(), i.blocking(), i.holdsShipment(), i.claimedAmount(), s.declaredValue(), i.dueAt(),
                    i.slaBreached(OffsetDateTime.now()), i.assigneeProfileId(), s.assigneeName(), i.withdrawnAt(),
                    i.reopenedAt(), i.reopenCount(), loc == null ? null : loc.latitude(),
                    loc == null ? null : loc.longitude(), i.occurredAt(), i.adjustmentId(),
                    s.attachments().stream().map(a -> new IncidentAttachmentView(a.documentId(), a.fileName(), a.type(),
                            a.contentType())).toList(),
                    mine && !i.blocking() && i.isActive());
        }
    }
}
