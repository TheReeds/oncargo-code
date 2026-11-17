package pe.logyx.incident.domain.model;

import pe.logyx.incident.domain.IncidentOutcomePolicy;
import pe.logyx.incident.domain.IncidentStatusPolicy;
import pe.logyx.shared.domain.ContactInfoPolicy;

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
    private IncidentShipmentOutcome shipmentOutcome;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime resolvedAt;

    public Incident(UUID id, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID reportedByOrgId,
                    UUID reportedByProfileId, IncidentCategory category, String description, IncidentStatus status,
                    String resolutionNotes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                    OffsetDateTime resolvedAt) {
        this(id, shipmentId, requesterOrgId, carrierOrgId, reportedByOrgId, reportedByProfileId, category, description,
                status, resolutionNotes, createdAt, updatedAt, resolvedAt, null);
    }

    public Incident(UUID id, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID reportedByOrgId,
                    UUID reportedByProfileId, IncidentCategory category, String description, IncidentStatus status,
                    String resolutionNotes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                    OffsetDateTime resolvedAt, IncidentShipmentOutcome shipmentOutcome) {
        this.shipmentOutcome = shipmentOutcome;
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
                reporterProfileId, category, ContactInfoPolicy.clean(description, "description"),       // RN07
                IncidentStatus.OPEN, null, now, now, null);
    }

    /**
     * Solo el operador cambia el estado; las transiciones válidas las define {@link IncidentStatusPolicy}. Si la
     * incidencia deja de estar activa, {@code outcome} (estado final del envío, RF55) es obligatorio y debe
     * corresponder a {@code shipmentStatus} ({@link IncidentOutcomePolicy}).
     */
    public void changeStatus(IncidentStatus target, String notes, IncidentShipmentOutcome outcome,
                             pe.logyx.shipment.domain.model.Shipment.Status shipmentStatus, OffsetDateTime now) {
        IncidentStatusPolicy.ensureTransition(status, target, notes);
        if (IncidentStatusPolicy.deactivates(status, target)) {
            IncidentOutcomePolicy.ensureValid(outcome, shipmentStatus);
            shipmentOutcome = outcome;
        }
        apply(target, notes, now);
    }

    /** Transiciones que no liberan el envío (IN_REVIEW, RESOLVED → CLOSED). */
    public void changeStatus(IncidentStatus target, String notes, OffsetDateTime now) {
        IncidentStatusPolicy.ensureTransition(status, target, notes);
        if (IncidentStatusPolicy.deactivates(status, target)) {
            throw pe.logyx.shared.domain.BusinessException.badRequest("LOGYX-INC-006",
                    "Indica el estado final del envío (RESUME, DELIVERED o CANCELLED)", "shipmentOutcome");
        }
        apply(target, notes, now);
    }

    private void apply(IncidentStatus target, String notes, OffsetDateTime now) {
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
    public IncidentShipmentOutcome shipmentOutcome() { return shipmentOutcome; }
}
