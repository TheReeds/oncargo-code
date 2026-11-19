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
import pe.logyx.notification.application.UnsubscribeLinks;
import pe.logyx.notification.application.port.out.NotificationMailer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Correo de avisos (RF50) con Quarkus Mailer. Se envía <b>después</b> de confirmar la transacción del despacho del
 * outbox o del job de la cola ({@code AFTER_SUCCESS}): si se revierte y se reintenta, no sale dos veces. Es de mejor
 * esfuerzo (un fallo del SMTP no reintenta el evento); el aviso in-app es el canal garantizado. En dev/test el mailer es
 * mock (dev: {@code GET /api/v1/dev/mailbox}). Texto + HTML simple, sin recursos remotos; enlaza a la web.
 *
 * <p>V24 (2.2): un correo puede traer varios avisos (resumen «Tienes N avisos»). Todos llevan «Darte de baja»
 * ({@code /baja?t=}) y las cabeceras {@code List-Unsubscribe} (https a la web) y {@code List-Unsubscribe-Post:
 * List-Unsubscribe=One-Click} (RFC 8058). {@code Reply-To} opcional: {@code logyx.mail.reply-to} (env
 * {@code MAIL_REPLY_TO}); el remitente es {@code quarkus.mailer.from} ({@code MAIL_FROM}).
 */
@ApplicationScoped
public class QuarkusNotificationMailer implements NotificationMailer {

    /** Correos pendientes de la transacción en curso. */
    public record Queued(List<Email> emails) {}

    @Inject Event<Queued> queued;
    @Inject ReactiveMailer mailer;
    @Inject UnsubscribeLinks unsubscribe;

    @ConfigProperty(name = "logyx.mail.reply-to") Optional<String> replyTo;

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
        String base = unsubscribe.base();
        String link = base + "/notificaciones";
        String prefs = base + "/cuenta/avisos";
        String token = unsubscribe.token(e.profileId(), e.category());
        String leave = unsubscribe.pageUrl(token);
        boolean digest = e.items().size() > 1;
        String subject = digest ? "Tienes " + e.items().size() + " avisos" : e.items().getFirst().title();
        String why = e.category() == null ? "por tus avisos de OnCargo" : "por tus avisos de «" + e.category().label() + "»";

        String textItems = digest
                ? e.items().stream().map(i -> "• " + i.title() + "\n  " + i.body()).collect(Collectors.joining("\n\n"))
                : e.items().getFirst().body();
        String text = """
                Hola %s:

                %s

                %s

                Ver en OnCargo: %s

                Recibes este correo %s. Cámbialos en %s
                Darte de baja: %s

                Equipo OnCargo
                """.formatted(e.fullName(), subject, textItems, link, why, prefs, leave);

        String htmlItems = digest
                ? e.items().stream().map(i -> """
                        <tr><td style="padding:12px 0;border-top:1px solid #e3e7ee">
                        <p style="margin:0 0 4px;font-weight:bold">%s</p><p style="margin:0;color:#37414f">%s</p>
                        <p style="margin:4px 0 0;color:#5b6b7f;font-size:12px">%s</p></td></tr>
                        """.formatted(esc(i.title()), esc(i.body()), esc(i.categoryLabel())))
                        .collect(Collectors.joining("", "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">", "</table>"))
                : "<p>" + esc(e.items().getFirst().body()) + "</p>";
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
                %3$s
                <p style="margin:24px 0"><a href="%4$s" style="background:#0b5cad;color:#ffffff;padding:12px 20px;
                border-radius:6px;text-decoration:none;font-weight:bold;display:inline-block">Ver en OnCargo</a></p>
                <p style="color:#555;font-size:13px">Recibes este correo %5$s.
                <a href="%6$s" style="color:#0b5cad">Cambia tus avisos</a> ·
                <a href="%7$s" style="color:#0b5cad">Darte de baja</a></p>
                <p style="color:#555;font-size:13px;margin-top:24px">Equipo OnCargo</p>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(esc(subject), esc(e.fullName()), htmlItems, esc(link), esc(why), esc(prefs), esc(leave));
        Mail mail = Mail.withText(e.to(), subject + " · OnCargo", text).setHtml(html)
                .addHeader("List-Unsubscribe", "<" + unsubscribe.oneClickUrl(token) + ">")
                .addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        replyTo.filter(r -> !r.isBlank()).ifPresent(mail::setReplyTo);
        return mail;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
