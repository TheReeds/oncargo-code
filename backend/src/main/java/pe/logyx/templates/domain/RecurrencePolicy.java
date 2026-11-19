package pe.logyx.templates.domain;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.templates.domain.model.ScheduleFrequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Fechas de una programación (roadmap 2.1). Todo en hora de Lima: la carga se recoge un día de la semana y se publica
 * {@code leadDays} antes, a las 06:00.
 */
public final class RecurrencePolicy {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    public static final LocalTime RUN_TIME = LocalTime.of(6, 0);
    public static final int MAX_LEAD_DAYS = 14;
    /** Fallos seguidos tras los que la programación se pausa sola. */
    public static final int MAX_FAILURES = 3;

    private RecurrencePolicy() {}

    /** Valida los parámetros que elige la persona. */
    public static void validate(DayOfWeek weekday, int leadDays, LocalDate endsOn, LocalDate today) {
        if (weekday == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el día de recojo", "pickupWeekday");
        }
        if (leadDays < 0 || leadDays > MAX_LEAD_DAYS) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "La anticipación va de 0 a " + MAX_LEAD_DAYS + " días", "leadDays");
        }
        if (endsOn != null && endsOn.isBefore(today)) {
            throw BusinessException.badRequest("LOGYX-TPL-003", "La fecha de fin ya pasó", "endsOn");
        }
    }

    /**
     * Primer recojo: el primer {@code weekday} desde hoy cuyo aviso (recojo − anticipación) no ha pasado, es decir,
     * se publica hoy o más adelante. Así crear la programación no publica de inmediato una carga para mañana por error.
     */
    public static LocalDate firstPickup(DayOfWeek weekday, int leadDays, LocalDate today) {
        LocalDate first = today.plusDays(leadDays).with(TemporalAdjusters.nextOrSame(weekday));
        return first;
    }

    /** El recojo que sigue al dado. */
    public static LocalDate following(LocalDate pickup, ScheduleFrequency frequency) {
        return pickup.plusDays(frequency.days);
    }

    /** Cuándo se publica el recojo dado. */
    public static OffsetDateTime runAt(LocalDate pickup, int leadDays) {
        return pickup.minusDays(leadDays).atTime(RUN_TIME).atZone(LIMA).toOffsetDateTime();
    }

    public static boolean isHoliday(LocalDate date) {
        return PeruHolidays.isHoliday(date);
    }
}
