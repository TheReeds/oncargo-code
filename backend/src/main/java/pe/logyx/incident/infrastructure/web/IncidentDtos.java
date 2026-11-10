package pe.logyx.incident.infrastructure.web;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class IncidentDtos {
    private IncidentDtos() {}

    public record ReportIncident(@NotNull IncidentCategory category,
                                 @NotBlank @Size(max = 2000) String description) {}

    public record AddIncidentComment(@NotBlank @Size(max = 2000) String body) {}

    public record ChangeIncidentStatus(@NotNull IncidentStatus status,
                                       @Size(max = 2000) String resolutionNotes) {}

    public record IncidentCommentView(UUID id, UUID authorOrgId, String authorName, String body,
                                      OffsetDateTime createdAt) {
        static IncidentCommentView of(IncidentComment c, IncidentSummary s) {
            return new IncidentCommentView(c.id(), c.authorOrgId(), s.name(c.authorOrgId()), c.body(), c.createdAt());
        }
    }

    /** {@code active} = OPEN o IN_REVIEW: el envío está congelado (RN11). {@code comments} solo en el detalle. */
    public record IncidentView(UUID id, UUID shipmentId, UUID requesterOrgId, String requesterName,
                               UUID carrierOrgId, String carrierName, UUID reportedByOrgId,
                               IncidentCategory category, String description, IncidentStatus status,
                               boolean active, String resolutionNotes, OffsetDateTime createdAt,
                               OffsetDateTime updatedAt, OffsetDateTime resolvedAt,
                               List<IncidentCommentView> comments) {
        static IncidentView of(IncidentSummary s) {
            Incident i = s.incident();
            return new IncidentView(i.id(), i.shipmentId(), i.requesterOrgId(), s.name(i.requesterOrgId()),
                    i.carrierOrgId(), s.name(i.carrierOrgId()), i.reportedByOrgId(), i.category(), i.description(),
                    i.status(), i.isActive(), i.resolutionNotes(), i.createdAt(), i.updatedAt(), i.resolvedAt(),
                    s.comments().stream().map(c -> IncidentCommentView.of(c, s)).toList());
        }
    }
}
