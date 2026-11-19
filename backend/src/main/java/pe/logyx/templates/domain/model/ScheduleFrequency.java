package pe.logyx.templates.domain.model;

/** Cada cuánto se repite la carga: cada semana o cada dos. */
public enum ScheduleFrequency {
    WEEKLY(7), BIWEEKLY(14);

    public final int days;

    ScheduleFrequency(int days) { this.days = days; }
}
