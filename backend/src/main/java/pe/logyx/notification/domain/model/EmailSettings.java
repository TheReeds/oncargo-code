package pe.logyx.notification.domain.model;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Preferencias de correo de una persona (V24, {@code user_preferences}), comunes a todas las categorías: correo activo,
 * inmediato o resumen diario a {@code digestHour} y horario de silencio ({@code quietFrom}–{@code quietTo}, hora de
 * Lima, puede cruzar la medianoche). Qué categorías llegan por correo sigue en {@link NotificationPreference}.
 * {@code unsubscribedAt}: cuándo se dio de baja de todo con el enlace del correo (informativo).
 */
public record EmailSettings(boolean emailEnabled, EmailMode mode, int digestHour, LocalTime quietFrom, LocalTime quietTo,
                            OffsetDateTime unsubscribedAt) {

    /** Sin fila: correo activo, inmediato, resumen a las 7:00 si lo elige, sin silencio. */
    public static final EmailSettings DEFAULT = new EmailSettings(true, EmailMode.IMMEDIATE, 7, null, null, null);

    public EmailSettings {
        Objects.requireNonNull(mode);
    }

    public boolean hasQuietHours() { return quietFrom != null && quietTo != null; }

    /** Copia sin correo (baja de todo con el enlace). */
    public EmailSettings unsubscribed(OffsetDateTime now) {
        return new EmailSettings(false, mode, digestHour, quietFrom, quietTo, now);
    }
}
