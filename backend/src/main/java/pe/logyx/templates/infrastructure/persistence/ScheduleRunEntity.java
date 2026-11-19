package pe.logyx.templates.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.templates.domain.model.ScheduleRun;
import pe.logyx.templates.domain.model.ScheduleRunStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_schedule_runs")
public class ScheduleRunEntity extends PanacheEntityBase {

    @Id
    public UUID id;
    @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "schedule_id") public UUID scheduleId;
    @Column(name = "template_id") public UUID templateId;
    @Column(name = "pickup_date") public LocalDate pickupDate;
    @Enumerated(EnumType.STRING) public ScheduleRunStatus status;
    @Column(name = "request_id") public UUID requestId;
    @Column(name = "error_code") public String errorCode;
    @Column(name = "error_message") public String errorMessage;
    @Column(name = "created_at") public OffsetDateTime createdAt;
    @Column(name = "resolved_at") public OffsetDateTime resolvedAt;

    ScheduleRun toDomain() {
        return new ScheduleRun(id, organizationId, scheduleId, templateId, pickupDate, status, requestId, errorCode,
                errorMessage, createdAt, resolvedAt);
    }

    void apply(ScheduleRun r) {
        id = r.id();
        organizationId = r.organizationId();
        scheduleId = r.scheduleId();
        templateId = r.templateId();
        pickupDate = r.pickupDate();
        status = r.status();
        requestId = r.requestId();
        errorCode = r.errorCode();
        errorMessage = r.errorMessage();
        createdAt = r.createdAt();
        resolvedAt = r.resolvedAt();
    }
}
