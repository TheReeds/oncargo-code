package pe.logyx.templates.domain.model;

import pe.logyx.templates.domain.RecurrencePolicy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Programación de una plantilla: «cada lunes» o «cada dos lunes», con anticipación, fecha de fin, salto de feriados y
 * modo (publicar sola o dejar borrador). Guarda la próxima ocurrencia ({@code nextPickupDate}) y cuándo toca
 * publicarla ({@code nextRunAt}). Una ocurrencia que falla no se reintenta: la programación avanza, y a los
 * {@link RecurrencePolicy#MAX_FAILURES} fallos seguidos se pausa.
 */
public final class RequestSchedule {

    /** Lo que elige la persona. */
    public record Config(ScheduleFrequency frequency, DayOfWeek pickupWeekday, int leadDays, LocalDate endsOn,
                         boolean skipHolidays, ScheduleMode mode) {}

    private final UUID id;
    private final UUID organizationId;
    private final UUID templateId;
    private final OffsetDateTime createdAt;
    private Config config;
    private boolean paused;
    private LocalDate nextPickupDate;
    private OffsetDateTime nextRunAt;
    private OffsetDateTime lastRunAt;
    private int failures;
    private OffsetDateTime finishedAt;
    private OffsetDateTime updatedAt;

    public RequestSchedule(UUID id, UUID organizationId, UUID templateId, Config config, boolean paused,
                           LocalDate nextPickupDate, OffsetDateTime nextRunAt, OffsetDateTime lastRunAt, int failures,
                           OffsetDateTime finishedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.templateId = templateId;
        this.config = config;
        this.paused = paused;
        this.nextPickupDate = nextPickupDate;
        this.nextRunAt = nextRunAt;
        this.lastRunAt = lastRunAt;
        this.failures = failures;
        this.finishedAt = finishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RequestSchedule create(UUID organizationId, UUID templateId, Config config, LocalDate today) {
        RequestSchedule s = new RequestSchedule(UUID.randomUUID(), organizationId, templateId, config, false, today,
                null, null, 0, null, OffsetDateTime.now(), OffsetDateTime.now());
        s.restart(config, today);
        return s;
    }

    /** Cambia la configuración y recalcula la próxima ocurrencia desde hoy; reanuda y limpia los fallos. */
    public void restart(Config config, LocalDate today) {
        RecurrencePolicy.validate(config.pickupWeekday(), config.leadDays(), config.endsOn(), today);
        this.config = config;
        this.paused = false;
        this.failures = 0;
        this.finishedAt = null;
        this.nextPickupDate = RecurrencePolicy.firstPickup(config.pickupWeekday(), config.leadDays(), today);
        settle();
    }

    public void pause() {
        paused = true;
        updatedAt = OffsetDateTime.now();
    }

    /** Reanuda desde la próxima ocurrencia que aún no pasó; una programación ya terminada no revive. */
    public void resume(LocalDate today) {
        if (finishedAt != null) return;
        paused = false;
        failures = 0;
        if (nextPickupDate.minusDays(config.leadDays()).isBefore(today)) {
            nextPickupDate = RecurrencePolicy.firstPickup(config.pickupWeekday(), config.leadDays(), today);
        }
        settle();
    }

    /** Se ejecutó la ocurrencia de {@code nextPickupDate}: pasa a la siguiente. {@code ok=false} cuenta un fallo. */
    public void advance(boolean ok, OffsetDateTime now) {
        lastRunAt = now;
        failures = ok ? 0 : failures + 1;
        if (failures >= RecurrencePolicy.MAX_FAILURES) paused = true;
        nextPickupDate = RecurrencePolicy.following(nextPickupDate, config.frequency());
        settle();
    }

    public boolean isDue(OffsetDateTime now) {
        return !paused && finishedAt == null && !nextRunAt.isAfter(now);
    }

    /** Fija {@code nextRunAt}; si el recojo pasa de la fecha de fin, la programación termina. */
    private void settle() {
        updatedAt = OffsetDateTime.now();
        if (config.endsOn() != null && nextPickupDate.isAfter(config.endsOn())) {
            finishedAt = updatedAt;
        }
        nextRunAt = RecurrencePolicy.runAt(nextPickupDate, config.leadDays());
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID templateId() { return templateId; }
    public Config config() { return config; }
    public boolean isPaused() { return paused; }
    public LocalDate nextPickupDate() { return nextPickupDate; }
    public OffsetDateTime nextRunAt() { return nextRunAt; }
    public OffsetDateTime lastRunAt() { return lastRunAt; }
    public int failures() { return failures; }
    public OffsetDateTime finishedAt() { return finishedAt; }
    public boolean isFinished() { return finishedAt != null; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}
