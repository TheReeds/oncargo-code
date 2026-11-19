package pe.logyx.incident.domain.model;

import pe.logyx.incident.domain.IncidentOutcomePolicy;
import pe.logyx.incident.domain.IncidentStatusPolicy;
import pe.logyx.shared.domain.ContactInfoPolicy;

import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Incidencia reportada sobre un envío. Mientras está activa y {@code blocking} congela el avance de paradas (RN11); las
 * informativas (V23: retraso, vía bloqueada, disputa de pago) avisan sin detener el viaje. V23 (roadmap 2.7): severidad
 * con su SLA ({@code dueAt}), monto reclamado, responsable del operador, retiro por quien la reportó (solo
 * informativas), reapertura por el operador, ubicación y hora del dispositivo (botón «Emergencia» sin señal) y vínculo
 * con un ajuste del envío.
 */
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
    // ----- V23
    private IncidentSeverity severity = IncidentSeverity.MEDIUM;
    private boolean blocking = true;
    private BigDecimal claimedAmount;
    private UUID assigneeProfileId;
    private OffsetDateTime dueAt;
    private OffsetDateTime withdrawnAt;
    private OffsetDateTime reopenedAt;
    private int reopenCount;
    private Location location;
    private OffsetDateTime occurredAt;
    private UUID adjustmentId;

    /** Ubicación del reporte (botón «Emergencia»). */
    public record Location(BigDecimal latitude, BigDecimal longitude) {}

    /**
     * Datos opcionales del alta (2.7). {@code severity} null = la de la categoría; {@code occurredAt} = hora del
     * dispositivo (reporte encolado sin señal), ya acotada.
     */
    public record Details(IncidentSeverity severity, BigDecimal claimedAmount, Location location,
                          OffsetDateTime occurredAt, UUID adjustmentId) {
        public static final Details NONE = new Details(null, null, null, null, null);
    }

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
        Incident i = new Incident(UUID.randomUUID(), shipmentId, requesterOrgId, carrierOrgId, reporterOrgId,
                reporterProfileId, category, ContactInfoPolicy.clean(description, "description"),       // RN07
                IncidentStatus.OPEN, null, now, now, null);
        i.severity = category.defaultSeverity();
        i.blocking = category.blocking();
        i.dueAt = now.plusHours(24);
        return i;
    }

    /** Alta v2 (2.7): severidad (o la de la categoría), SLA según la severidad, monto reclamado y ubicación. */
    public static Incident report(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID reporterOrgId,
                                  UUID reporterProfileId, IncidentCategory category, String description,
                                  Details details, java.util.function.Function<IncidentSeverity, Duration> sla) {
        Incident i = report(shipmentId, requesterOrgId, carrierOrgId, reporterOrgId, reporterProfileId, category,
                description);
        Details d = details == null ? Details.NONE : details;
        if (d.severity() != null) i.severity = d.severity();
        if (d.claimedAmount() != null) {
            if (d.claimedAmount().signum() <= 0 || d.claimedAmount().stripTrailingZeros().scale() > 2) {
                throw BusinessException.badRequest("LOGYX-INC-010", "El monto reclamado debe ser mayor a 0",
                        "claimedAmount");
            }
            i.claimedAmount = d.claimedAmount();
        }
        i.location = d.location();
        i.occurredAt = d.occurredAt();
        i.adjustmentId = d.adjustmentId();
        i.dueAt = i.createdAt.plus(sla.apply(i.severity));
        return i;
    }

    /**
     * Quien la reportó la retira (2.7): solo una informativa y activa (LOGYX-INC-007). Queda CLOSED con la marca de
     * retiro; no fija estado final del envío (no lo había congelado).
     */
    public void withdraw(UUID byOrgId, OffsetDateTime now) {
        if (!byOrgId.equals(reportedByOrgId)) {
            throw new BusinessException(403, "LOGYX-INC-007", "Solo quien reportó la incidencia la retira");
        }
        if (blocking || !isActive()) {
            throw BusinessException.conflict("LOGYX-INC-007",
                    "Solo se retira una incidencia informativa que sigue abierta");
        }
        status = IncidentStatus.CLOSED;
        withdrawnAt = now;
        updatedAt = now;
        resolvedAt = now;
        resolutionNotes = "Retirada por quien la reportó";
    }

    /**
     * El operador la reabre (2.7): una resuelta o cerrada (no retirada) vuelve a IN_REVIEW con un SLA nuevo y sin
     * estado final del envío (LOGYX-INC-008). Si congela y el envío sigue en curso, vuelve a congelarlo (RN11).
     */
    public void reopen(String reason, OffsetDateTime now, Duration sla) {
        ensureReopenable();
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("LOGYX-INC-003", "Indica por qué se reabre", "reason");
        }
        status = IncidentStatus.IN_REVIEW;
        resolutionNotes = reason.trim();
        shipmentOutcome = null;
        resolvedAt = null;
        reopenedAt = now;
        reopenCount++;
        updatedAt = now;
        dueAt = now.plus(sla);
    }

    /** LOGYX-INC-008: ya abierta o retirada. */
    public void ensureReopenable() {
        if (isActive() || withdrawnAt != null) {
            throw BusinessException.conflict("LOGYX-INC-008",
                    isActive() ? "La incidencia ya está abierta" : "Una incidencia retirada no se reabre");
        }
    }

    /** El operador toma la incidencia o la asigna a otra persona del operador (null = sin responsable). */
    public void assign(UUID profileId, OffsetDateTime now) {
        if (status == IncidentStatus.CLOSED) {
            throw BusinessException.conflict("LOGYX-INC-004", "La incidencia está cerrada");
        }
        assigneeProfileId = profileId;
        updatedAt = now;
    }

    /** Venció su SLA y sigue activa. */
    public boolean slaBreached(OffsetDateTime now) {
        return isActive() && dueAt != null && now.isAfter(dueAt);
    }

    /** Restaura lo guardado (persistencia). */
    public Incident restoreV2(IncidentSeverity severity, boolean blocking, BigDecimal claimedAmount,
                              UUID assigneeProfileId, OffsetDateTime dueAt, OffsetDateTime withdrawnAt,
                              OffsetDateTime reopenedAt, int reopenCount, Location location,
                              OffsetDateTime occurredAt, UUID adjustmentId) {
        this.severity = severity == null ? IncidentSeverity.MEDIUM : severity;
        this.blocking = blocking;
        this.claimedAmount = claimedAmount;
        this.assigneeProfileId = assigneeProfileId;
        this.dueAt = dueAt;
        this.withdrawnAt = withdrawnAt;
        this.reopenedAt = reopenedAt;
        this.reopenCount = reopenCount;
        this.location = location;
        this.occurredAt = occurredAt;
        this.adjustmentId = adjustmentId;
        return this;
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
            // Una informativa no congeló el envío: el estado final es opcional (si se indica, debe corresponder).
            if (blocking || outcome != null) IncidentOutcomePolicy.ensureValid(outcome, shipmentStatus);
            shipmentOutcome = outcome;
        }
        apply(target, notes, now);
    }

    /** Transiciones que no liberan el envío (IN_REVIEW, RESOLVED → CLOSED). */
    public void changeStatus(IncidentStatus target, String notes, OffsetDateTime now) {
        IncidentStatusPolicy.ensureTransition(status, target, notes);
        if (IncidentStatusPolicy.deactivates(status, target) && blocking) {
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

    /** Activa y de las que congelan el envío (RN11). */
    public boolean holdsShipment() { return isActive() && blocking; }

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
    public IncidentSeverity severity() { return severity; }
    public boolean blocking() { return blocking; }
    public BigDecimal claimedAmount() { return claimedAmount; }
    public UUID assigneeProfileId() { return assigneeProfileId; }
    public OffsetDateTime dueAt() { return dueAt; }
    public OffsetDateTime withdrawnAt() { return withdrawnAt; }
    public OffsetDateTime reopenedAt() { return reopenedAt; }
    public int reopenCount() { return reopenCount; }
    public Location location() { return location; }
    public OffsetDateTime occurredAt() { return occurredAt; }
    public UUID adjustmentId() { return adjustmentId; }
}
