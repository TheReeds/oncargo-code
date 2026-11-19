package pe.logyx.notification.application.port.out;

import java.util.List;

/** Canal de correo de los avisos (RF50). Se envía después de confirmar la transacción del despacho (mejor esfuerzo). */
public interface NotificationMailer {

    record Email(String to, String fullName, String title, String body, String categoryLabel) {}

    /** Encola los correos; se envían solo si la transacción en curso se confirma. */
    void queue(List<Email> emails);
}
