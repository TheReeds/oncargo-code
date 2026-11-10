package pe.logyx.incident.domain.model;

import pe.logyx.incident.domain.IncidentStatusPolicy;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Incidencia reportada sobre un envío. Mientras está activa congela el avance de paradas (RN11). */
public final class Incident {

    private final UUID id;
    private final UUID shipmentId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final UUID reportedByOrgId;
    private final UUID reportedByProfileId;
    private final IncidentCategory category;
    private final String description;
    private IncidentStatus status;
    private String resolutionNotes;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime resolvedAt;

    public Incident(UUID id, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID reportedByOrgId,
                    UUID reportedByProfileId, IncidentCategory category, String description, IncidentStatus status,
                    String resolutionNotes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                    OffsetDateTime resolvedAt) {
        this.id = id;
        this.shipmentId = shipmentId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.reportedByOrgId = reportedByOrgId;
        this.reportedByProfileId = reportedByProfileId;
        this.category = category;
        this.description = description;
        this.status = status;
        this.resolutionNotes = resolutionNotes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.resolvedAt = resolvedAt;
    }

    public static Incident report(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID reporterOrgId,
                                  UUID reporterProfileId, IncidentCategory category, String description) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Incident(UUID.randomUUID(), shipmentId, requesterOrgId, carrierOrgId, reporterOrgId,
                reporterProfileId, category, description.trim(), IncidentStatus.OPEN, null, now, now, null);
    }

    /** Solo el operador cambia el estado; las transiciones válidas las define {@link IncidentStatusPolicy}. */
    public void changeStatus(IncidentStatus target, String notes, OffsetDateTime now) {
        IncidentStatusPolicy.ensureTransition(status, target, notes);
        if (notes != null && !notes.isBlank()) resolutionNotes = notes.trim();
        if (target == IncidentStatus.RESOLVED || (target == IncidentStatus.CLOSED && resolvedAt == null)) resolvedAt = now;
        status = target;
        updatedAt = now;
    }

    public boolean isActive() { return status.isActive(); }

    public boolean isParty(UUID orgId) { return orgId.equals(requesterOrgId) || orgId.equals(carrierOrgId); }

    public UUID id() { return id; }
    public UUID shipmentId() { return shipmentId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public UUID reportedByOrgId() { return reportedByOrgId; }
    public UUID reportedByProfileId() { return reportedByProfileId; }
    public IncidentCategory category() { return category; }
    public String description() { return description; }
    public IncidentStatus status() { return status; }
    public String resolutionNotes() { return resolutionNotes; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
    public OffsetDateTime resolvedAt() { return resolvedAt; }
}
