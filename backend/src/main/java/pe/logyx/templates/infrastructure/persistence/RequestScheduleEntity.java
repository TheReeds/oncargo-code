package pe.logyx.templates.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.templates.domain.model.RequestSchedule;
import pe.logyx.templates.domain.model.ScheduleFrequency;
import pe.logyx.templates.domain.model.ScheduleMode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_schedules")
public class RequestScheduleEntity extends PanacheEntityBase {

    @Id
    public UUID id;
    @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "template_id") public UUID templateId;
    @Enumerated(EnumType.STRING) public ScheduleFrequency frequency;
    @Column(name = "pickup_weekday") public short pickupWeekday;
    @Column(name = "lead_days") public short leadDays;
    @Column(name = "ends_on") public LocalDate endsOn;
    @Column(name = "skip_holidays") public boolean skipHolidays;
    @Enumerated(EnumType.STRING) public ScheduleMode mode;
    public boolean paused;
    @Column(name = "next_pickup_date") public LocalDate nextPickupDate;
    @Column(name = "next_run_at") public OffsetDateTime nextRunAt;
    @Column(name = "last_run_at") public OffsetDateTime lastRunAt;
    public short failures;
    @Column(name = "finished_at") public OffsetDateTime finishedAt;
    @Column(name = "created_at") public OffsetDateTime createdAt;
    @Column(name = "updated_at") public OffsetDateTime updatedAt;

    RequestSchedule toDomain() {
        return new RequestSchedule(id, organizationId, templateId,
                new RequestSchedule.Config(frequency, DayOfWeek.of(pickupWeekday), leadDays, endsOn, skipHolidays, mode),
                paused, nextPickupDate, nextRunAt, lastRunAt, failures, finishedAt, createdAt, updatedAt);
    }

    void apply(RequestSchedule s) {
        var c = s.config();
        id = s.id();
        organizationId = s.organizationId();
        templateId = s.templateId();
        frequency = c.frequency();
        pickupWeekday = (short) c.pickupWeekday().getValue();
        leadDays = (short) c.leadDays();
        endsOn = c.endsOn();
        skipHolidays = c.skipHolidays();
        mode = c.mode();
        paused = s.isPaused();
        nextPickupDate = s.nextPickupDate();
        nextRunAt = s.nextRunAt();
        lastRunAt = s.lastRunAt();
        failures = (short) s.failures();
        finishedAt = s.finishedAt();
        createdAt = s.createdAt();
        updatedAt = s.updatedAt();
    }
}
