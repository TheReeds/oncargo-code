package pe.logyx.notification.domain;

import pe.logyx.notification.domain.model.EmailMode;
import pe.logyx.notification.domain.model.EmailSettings;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Cuándo sale el correo de un aviso (V24, roadmap 2.2), en Java puro. Lo decide la capa de {@code user_preferences}
 * después de la matriz categoría × canal (V16):
 * <ol>
 *   <li>correo desactivado → no sale ({@link Kind#SKIP});</li>
 *   <li>resumen diario → a la cola hasta la próxima {@code digestHour} de Lima ({@code DIGEST}); si esa hora cae en el
 *       silencio, hasta que termine;</li>
 *   <li>dentro del horario de silencio → a la cola hasta {@code quietTo} ({@code QUIET});</li>
 *   <li>si no, al momento ({@link Kind#NOW}).</li>
 * </ol>
 * El silencio es {@code [quietFrom, quietTo)} y puede cruzar la medianoche (22:00–07:00). Lima no tiene horario de
 * verano (UTC−5 todo el año), así que sumar días no salta ni repite horas.
 */
public final class EmailDeliveryPolicy {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    public enum Kind { SKIP, NOW, DEFER }

    /** {@code reason} y {@code sendAfter} solo con {@link Kind#DEFER}. */
    public record Decision(Kind kind, String reason, ZonedDateTime sendAfter) {
        static final Decision SKIP = new Decision(Kind.SKIP, null, null);
        static final Decision NOW = new Decision(Kind.NOW, null, null);

        static Decision defer(String reason, ZonedDateTime at) { return new Decision(Kind.DEFER, reason, at); }
    }

    public static final String QUIET = "QUIET";
    public static final String DIGEST = "DIGEST";

    private EmailDeliveryPolicy() {}

    public static Decision decide(EmailSettings s, ZonedDateTime now) {
        EmailSettings settings = s == null ? EmailSettings.DEFAULT : s;
        if (!settings.emailEnabled()) return Decision.SKIP;
        ZonedDateTime lima = now.withZoneSameInstant(LIMA);
        if (settings.mode() == EmailMode.DAILY) {
            ZonedDateTime at = nextDigest(lima, settings.digestHour());
            if (settings.hasQuietHours() && inQuiet(at.toLocalTime(), settings.quietFrom(), settings.quietTo())) {
                at = quietEnd(at, settings.quietFrom(), settings.quietTo());
            }
            return Decision.defer(DIGEST, at);
        }
        if (settings.hasQuietHours() && inQuiet(lima.toLocalTime(), settings.quietFrom(), settings.quietTo())) {
            return Decision.defer(QUIET, quietEnd(lima, settings.quietFrom(), settings.quietTo()));
        }
        return Decision.NOW;
    }

    /** true si {@code t} está en {@code [from, to)}; con {@code from > to} el tramo cruza la medianoche. */
    public static boolean inQuiet(LocalTime t, LocalTime from, LocalTime to) {
        if (from.equals(to)) return false;
        return from.isBefore(to) ? !t.isBefore(from) && t.isBefore(to) : !t.isBefore(from) || t.isBefore(to);
    }

    /** Fin del tramo de silencio en el que está {@code at} (hoy o mañana a {@code to}). */
    static ZonedDateTime quietEnd(ZonedDateTime at, LocalTime from, LocalTime to) {
        ZonedDateTime todayEnd = at.truncatedTo(ChronoUnit.DAYS).with(to);
        // Si cruza la medianoche y estamos en la parte de la noche (>= from), termina mañana.
        return from.isAfter(to) && !at.toLocalTime().isBefore(from) ? todayEnd.plusDays(1) : todayEnd;
    }

    /** Próxima {@code hour}:00 de Lima estrictamente posterior a {@code now} (a la misma hora en punto = mañana). */
    static ZonedDateTime nextDigest(ZonedDateTime now, int hour) {
        ZonedDateTime today = now.truncatedTo(ChronoUnit.DAYS).withHour(hour);
        return today.isAfter(now) ? today : today.plusDays(1);
    }

    /** {@code LOGYX-NTF-004}: horario de silencio incompleto o de duración cero; {@code NTF-005}: hora del resumen. */
    public static void validate(EmailSettings s) {
        if ((s.quietFrom() == null) != (s.quietTo() == null)) {
            throw BusinessException.badRequest("LOGYX-NTF-004", "Indica la hora de inicio y la de fin del horario de silencio.",
                    s.quietFrom() == null ? "quietFrom" : "quietTo");
        }
        if (s.hasQuietHours() && s.quietFrom().equals(s.quietTo())) {
            throw BusinessException.badRequest("LOGYX-NTF-004", "El horario de silencio debe empezar y terminar a horas distintas.",
                    "quietTo");
        }
        if (s.digestHour() < 0 || s.digestHour() > 23) {
            throw BusinessException.badRequest("LOGYX-NTF-005", "La hora del resumen diario va de 0 a 23.", "digestHour");
        }
    }
}
