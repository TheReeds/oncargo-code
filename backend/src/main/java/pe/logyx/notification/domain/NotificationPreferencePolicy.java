package pe.logyx.notification.domain;

import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;
import pe.logyx.shared.domain.BusinessException;

import java.util.EnumMap;
import java.util.Map;

/**
 * Preferencias de aviso por persona (V16, roadmap 2.2 adelantado a la Ola 1), en Java puro:
 * <ul>
 *   <li>Por omisión todo llega in-app; por correo solo lo que exige actuar o es grave: adjudicaciones, envíos,
 *       incidencias y cuenta.</li>
 *   <li>{@code ACCOUNT} in-app es obligatoria (verificación de la empresa, pedidos de acceso): no se puede apagar
 *       ({@code LOGYX-NTF-001}). El correo sí.</li>
 * </ul>
 */
public final class NotificationPreferencePolicy {

    private NotificationPreferencePolicy() {}

    public static NotificationPreference defaults(NotificationCategory c) {
        boolean email = switch (c) {
            case AWARDS, SHIPMENTS, INCIDENTS, ACCOUNT -> true;
            default -> false;
        };
        return new NotificationPreference(c, true, email);
    }

    /** true si el canal in-app de la categoría no se puede desactivar. */
    public static boolean inAppLocked(NotificationCategory c) {
        return c == NotificationCategory.ACCOUNT;
    }

    /** Preferencias efectivas: lo guardado o, si falta, el valor por defecto. */
    public static Map<NotificationCategory, NotificationPreference> effective(Map<NotificationCategory, NotificationPreference> stored) {
        Map<NotificationCategory, NotificationPreference> out = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory c : NotificationCategory.values()) {
            NotificationPreference p = stored == null ? null : stored.get(c);
            out.put(c, p == null ? defaults(c) : p);
        }
        return out;
    }

    public static void validate(NotificationPreference p) {
        if (inAppLocked(p.category()) && !p.inApp()) {
            throw BusinessException.badRequest("LOGYX-NTF-001",
                    "Los avisos de «" + p.category().label() + "» siempre llegan a la app: solo puedes apagar el correo.",
                    "inApp");
        }
    }
}
