package pe.logyx.shared.domain;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** Hora del día como texto {@code HH:mm} en los contratos (ventanas horarias de recojo y entrega). */
public final class TimeOfDay {

    /** Para {@code @Pattern} de los DTOs. */
    public static final String PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private TimeOfDay() {}

    public static LocalTime parse(String text, String field) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Hora inválida (use HH:mm): " + text, field);
        }
    }

    public static String format(LocalTime time) {
        return time == null ? null : String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
