package pe.logyx.notification.application.port.out;

import pe.logyx.notification.domain.model.NotificationCategory;

import java.util.List;
import java.util.UUID;

/**
 * Canal de correo de los avisos (RF50). Se envía después de confirmar la transacción del despacho o del job de la cola
 * (mejor esfuerzo). V24: cada correo es de una persona ({@code profileId}, para su enlace de baja) y lleva uno o varios
 * avisos ({@code items}: varios = resumen «Tienes N avisos»). {@code category} = la de todos los avisos, o null si
 * mezcla categorías (el enlace de baja es entonces de todo el correo de avisos).
 */
public interface NotificationMailer {

    record Item(String title, String body, String categoryLabel) {}

    record Email(String to, String fullName, UUID profileId, NotificationCategory category, List<Item> items) {
        public Email {
            items = List.copyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("Un correo de aviso necesita al menos un aviso");
        }

        public static Email single(String to, String fullName, UUID profileId, NotificationCategory category, String title,
                                   String body) {
            return new Email(to, fullName, profileId, category, List.of(new Item(title, body, category.label())));
        }
    }

    /** Encola los correos; se envían solo si la transacción en curso se confirma. */
    void queue(List<Email> emails);
}
