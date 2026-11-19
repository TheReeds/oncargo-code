package pe.logyx.templates.domain.model;

import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Una ocurrencia de la programación: qué pasó con el recojo de {@code pickupDate}. */
public final class ScheduleRun {

    private final UUID id;
    private final UUID organizationId;
    private final UUID scheduleId;
    private final UUID templateId;
    private final LocalDate pickupDate;
    private ScheduleRunStatus status;
    private UUID requestId;
    private final String errorCode;
    private final String errorMessage;
    private final OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;

    public ScheduleRun(UUID id, UUID organizationId, UUID scheduleId, UUID templateId, LocalDate pickupDate,
                       ScheduleRunStatus status, UUID requestId, String errorCode, String errorMessage,
                       OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.scheduleId = scheduleId;
        this.templateId = templateId;
        this.pickupDate = pickupDate;
        this.status = status;
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public static ScheduleRun of(RequestSchedule s, LocalDate pickupDate, ScheduleRunStatus status, UUID requestId,
                                 String errorCode, String errorMessage) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ScheduleRun(UUID.randomUUID(), s.organizationId(), s.id(), s.templateId(), pickupDate, status,
                requestId, errorCode, errorMessage == null ? null
                : errorMessage.substring(0, Math.min(300, errorMessage.length())), now,
                status == ScheduleRunStatus.DRAFT ? null : now);
    }

    /** El borrador se publicó como {@code requestId}. */
    public void published(UUID requestId) {
        ensureDraft();
        this.status = ScheduleRunStatus.PUBLISHED;
        this.requestId = requestId;
        this.resolvedAt = OffsetDateTime.now();
    }

    public void discard() {
        ensureDraft();
        this.status = ScheduleRunStatus.DISCARDED;
        this.resolvedAt = OffsetDateTime.now();
    }

    private void ensureDraft() {
        if (status != ScheduleRunStatus.DRAFT) {
            throw BusinessException.conflict("LOGYX-TPL-004", "Ese borrador ya se resolvió");
        }
    }

    public boolean isDraft() { return status == ScheduleRunStatus.DRAFT; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID scheduleId() { return scheduleId; }
    public UUID templateId() { return templateId; }
    public LocalDate pickupDate() { return pickupDate; }
    public ScheduleRunStatus status() { return status; }
    public UUID requestId() { return requestId; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime resolvedAt() { return resolvedAt; }
}
