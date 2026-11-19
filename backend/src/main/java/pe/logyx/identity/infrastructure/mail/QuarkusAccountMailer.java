package pe.logyx.identity.infrastructure.mail;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.identity.application.port.out.AccountMailer;

import java.time.Duration;

/**
 * Correos de cuenta con Quarkus Mailer. En dev/test {@code quarkus.mailer.mock=true}: no salen (en test se leen con
 * {@code MockMailbox}; en dev, también con {@code GET /api/v1/dev/mailbox}). En prod, SMTP por variables de entorno
 * ({@code SMTP_*}, {@code MAIL_FROM}). Texto + HTML simple, sin imágenes ni recursos remotos. Nunca loguea el enlace.
 */
@ApplicationScoped
public class QuarkusAccountMailer implements AccountMailer {

    @Inject Mailer mailer;

    @Override
    public void sendPasswordReset(String to, String fullName, String link, Duration validFor) {
        String validity = human(validFor);
        String text = """
                Hola %s:

                Recibimos un pedido para restablecer la contraseña de tu cuenta de OnCargo.
                Para crear una nueva, abre este enlace (vence en %s y solo se puede usar una vez):

                %s

                Si no lo pediste, ignora este correo: tu contraseña no cambia.

                Equipo OnCargo
                """.formatted(fullName, validity, link);
        String html = layout("Restablece tu contraseña", """
                <p>Hola %s:</p>
                <p>Recibimos un pedido para restablecer la contraseña de tu cuenta de OnCargo.</p>
                %s
                <p style="color:#555;font-size:14px">El enlace vence en %s y solo se puede usar una vez.
                Si no lo pediste, ignora este correo: tu contraseña no cambia.</p>
                """.formatted(esc(fullName), button(link, "Crear nueva contraseña"), validity), link);
        send(to, "Restablece tu contraseña de OnCargo", text, html);
    }

    @Override
    public void sendInvitation(String to, String fullName, String organizationName, String invitedByName, String link,
                               Duration validFor) {
        String validity = human(validFor);
        String who = invitedByName == null ? "Un administrador" : invitedByName;
        String text = """
                Hola %s:

                %s te invitó a unirte a %s en OnCargo, el marketplace de fletes del corredor Lima–Sierra Sur.
                Para activar tu cuenta y crear tu contraseña, abre este enlace (vence en %s):

                %s

                Si no esperabas esta invitación, ignora este correo.

                Equipo OnCargo
                """.formatted(fullName, who, organizationName, validity, link);
        String html = layout("Te invitaron a OnCargo", """
                <p>Hola %s:</p>
                <p>%s te invitó a unirte a <strong>%s</strong> en OnCargo, el marketplace de fletes del corredor
                Lima–Sierra Sur.</p>
                %s
                <p style="color:#555;font-size:14px">El enlace vence en %s. Si no esperabas esta invitación, ignora
                este correo.</p>
                """.formatted(esc(fullName), esc(who), esc(organizationName), button(link, "Activar mi cuenta"),
                validity), link);
        send(to, "Te invitaron a " + organizationName + " en OnCargo", text, html);
    }

    @Override
    public void sendOrganizationAccessRequest(String to, String adminName, String organizationName,
                                              String requesterName, String requesterEmail, String link) {
        String text = """
                Hola %s:

                %s (%s) intentó registrarse en OnCargo con el RUC de %s y pide unirse a tu organización.
                No verificamos ese correo. Si conoces a esta persona, invítala desde Usuarios y roles:

                %s

                Si no la conoces, ignora este correo.

                Equipo OnCargo
                """.formatted(adminName, requesterName, requesterEmail, organizationName, link);
        String html = layout("Piden unirse a tu organización", """
                <p>Hola %s:</p>
                <p><strong>%s</strong> (%s) intentó registrarse en OnCargo con el RUC de <strong>%s</strong> y pide
                unirse a tu organización.</p>
                <p style="color:#555;font-size:14px">No verificamos ese correo. Si conoces a esta persona, invítala
                desde Usuarios y roles. Si no, ignora este correo.</p>
                %s
                """.formatted(esc(adminName), esc(requesterName), esc(requesterEmail), esc(organizationName),
                button(link, "Abrir Usuarios y roles")), link);
        send(to, "Piden unirse a " + organizationName + " en OnCargo", text, html);
    }

    private void send(String to, String subject, String text, String html) {
        try {
            mailer.send(Mail.withText(to, subject, text).setHtml(html));
        } catch (RuntimeException e) {
            // Sin datos personales ni el enlace en el log.
            Log.errorf("No se pudo enviar un correo de cuenta (%s)", e.getClass().getSimpleName());
        }
    }

    private static String layout(String title, String body, String link) {
        return """
                <!doctype html>
                <html lang="es-PE"><head><meta charset="utf-8"><title>%s</title></head>
                <body style="margin:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1d2433">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center"
                style="padding:24px 12px">
                <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border-radius:8px"
                cellpadding="0" cellspacing="0"><tr><td style="padding:28px">
                <p style="margin:0 0 20px;font-size:20px;font-weight:bold;color:#0b5cad">OnCargo</p>
                <h1 style="margin:0 0 16px;font-size:20px">%s</h1>
                %s
                <p style="color:#555;font-size:13px">Si el botón no funciona, copia este enlace en tu navegador:<br>
                <span style="word-break:break-all">%s</span></p>
                <p style="color:#555;font-size:13px;margin-top:24px">Equipo OnCargo</p>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(esc(title), esc(title), body, esc(link));
    }

    private static String button(String link, String label) {
        return "<p style=\"margin:24px 0\"><a href=\"" + esc(link) + "\" style=\"background:#0b5cad;color:#ffffff;"
                + "padding:12px 20px;border-radius:6px;text-decoration:none;font-weight:bold;display:inline-block\">"
                + esc(label) + "</a></p>";
    }

    static String human(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 120) return minutes + " minutos";
        long hours = d.toHours();
        if (hours < 48) return hours + " horas";
        return d.toDays() + " días";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
