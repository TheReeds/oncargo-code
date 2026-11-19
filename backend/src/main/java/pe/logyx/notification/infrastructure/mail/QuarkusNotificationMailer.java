package pe.logyx.notification.infrastructure.mail;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.notification.application.port.out.NotificationMailer;

import java.util.List;

/**
 * Correo de avisos (RF50) con Quarkus Mailer. Se envía <b>después</b> de confirmar la transacción del despacho del
 * outbox ({@code AFTER_SUCCESS}): si el despacho se revierte y se reintenta, no sale dos veces. Es de mejor esfuerzo
 * (un fallo del SMTP no reintenta el evento); el aviso in-app es el canal garantizado. En dev/test el mailer es mock
 * (dev: {@code GET /api/v1/dev/mailbox}). Texto + HTML simple, sin recursos remotos; enlaza a la web.
 */
@ApplicationScoped
public class QuarkusNotificationMailer implements NotificationMailer {

    /** Correos pendientes de la transacción en curso. */
    public record Queued(List<Email> emails) {}

    @Inject Event<Queued> queued;
    @Inject ReactiveMailer mailer;

    @ConfigProperty(name = "logyx.web.url", defaultValue = "https://oncargo.tech")
    String webUrl;

    @Override
    public void queue(List<Email> emails) {
        if (!emails.isEmpty()) queued.fire(new Queued(List.copyOf(emails)));
    }

    void afterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) Queued q) {
        for (Email e : q.emails()) {
            mailer.send(toMail(e)).subscribe().with(ok -> {},
                    err -> Log.errorf("No se pudo enviar un correo de aviso (%s)", err.getClass().getSimpleName()));
        }
    }

    Mail toMail(Email e) {
        String base = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
        String link = base + "/notificaciones";
        String prefs = base + "/cuenta/avisos";
        String text = """
                Hola %s:

                %s

                %s

                Ver en OnCargo: %s

                Recibes este correo por tus avisos de «%s». Cámbialos en %s

                Equipo OnCargo
                """.formatted(e.fullName(), e.title(), e.body(), link, e.categoryLabel(), prefs);
        String html = """
                <!doctype html>
                <html lang="es-PE"><head><meta charset="utf-8"><title>%1$s</title></head>
                <body style="margin:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1d2433">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center"
                style="padding:24px 12px">
                <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border-radius:8px"
                cellpadding="0" cellspacing="0"><tr><td style="padding:28px">
                <p style="margin:0 0 20px;font-size:20px;font-weight:bold;color:#0b5cad">OnCargo</p>
                <p style="margin:0 0 8px">Hola %2$s:</p>
                <h1 style="margin:0 0 12px;font-size:20px">%1$s</h1>
                <p>%3$s</p>
                <p style="margin:24px 0"><a href="%4$s" style="background:#0b5cad;color:#ffffff;padding:12px 20px;
                border-radius:6px;text-decoration:none;font-weight:bold;display:inline-block">Ver en OnCargo</a></p>
                <p style="color:#555;font-size:13px">Recibes este correo por tus avisos de «%5$s».
                <a href="%6$s" style="color:#0b5cad">Cambia tus avisos</a>.</p>
                <p style="color:#555;font-size:13px;margin-top:24px">Equipo OnCargo</p>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(esc(e.title()), esc(e.fullName()), esc(e.body()), esc(link), esc(e.categoryLabel()), esc(prefs));
        return Mail.withText(e.to(), e.title() + " · OnCargo", text).setHtml(html);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
