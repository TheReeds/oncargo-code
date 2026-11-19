package pe.logyx.notification.domain.model;

import java.util.Objects;

/** Por qué canales recibe una persona los avisos de una categoría (V16). */
public record NotificationPreference(NotificationCategory category, boolean inApp, boolean email) {
    public NotificationPreference {
        Objects.requireNonNull(category);
    }
}
