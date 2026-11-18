package pe.logyx.support;

import io.quarkus.arc.Arc;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Correos del mailer mock en tests (el envío de "olvidé mi contraseña" es asíncrono: se espera). */
public final class Mailbox {

    private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]{43})");

    private Mailbox() {}

    public static List<Mail> to(String email) {
        List<Mail> m = Arc.container().instance(MockMailbox.class).get().getMailsSentTo(email.toLowerCase());
        return m == null ? List.of() : m;
    }

    /** Espera hasta {@code count} correos para {@code email} (máx. 10 s) y devuelve el último. */
    public static Mail await(String email, int count) {
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            List<Mail> m = to(email);
            if (m.size() >= count) return m.get(m.size() - 1);
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("No llegó el correo " + count + " para " + email + " (hay " + to(email).size() + ")");
    }

    /** Token del enlace del correo. */
    public static String token(Mail mail) {
        Matcher m = TOKEN.matcher(mail.getText());
        if (!m.find()) throw new AssertionError("El correo no trae enlace con token");
        return m.group(1);
    }

    /** Da tiempo a que el trabajo asíncrono termine (para comprobar que NO llegó un correo). */
    public static void settle() {
        try { Thread.sleep(700); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
