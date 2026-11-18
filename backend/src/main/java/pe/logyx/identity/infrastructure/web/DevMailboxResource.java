package pe.logyx.identity.infrastructure.web;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Buzón de desarrollo para e2e y pruebas manuales: últimos correos del {@link MockMailbox} (mailer en modo mock).
 * Solo existe si se compila con {@code logyx.dev-mailbox=true} (propiedad de build: {@code %dev} sí; prod no; la imagen
 * de pruebas puede compilarse con {@code -Dlogyx.dev-mailbox=true}, como los datos demo). Fuera del contrato OpenAPI.
 */
@IfBuildProperty(name = "logyx.dev-mailbox", stringValue = "true")
@Path("/api/v1")
public class DevMailboxResource {

    public record DevMail(String to, String subject, String text, String link, String token) {}

    private static final Pattern LINK = Pattern.compile("https?://\\S+?[?&]token=([A-Za-z0-9_%-]+)");

    @Inject Instance<MockMailbox> mailbox;

    @GET
    @Path("/dev/mailbox")
    @PermitAll
    @Operation(hidden = true)
    public ApiResponse<List<DevMail>> mailbox(@QueryParam("to") String to) {
        if (!mailbox.isResolvable()) {
            throw new BusinessException(404, "LOGYX-GEN-404", "El mailer no está en modo mock (quarkus.mailer.mock)");
        }
        if (to == null || to.isBlank()) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el destinatario (?to=)", "to");
        }
        List<Mail> sent = mailbox.get().getMailsSentTo(to.trim().toLowerCase());
        List<Mail> mails = new ArrayList<>(sent == null ? List.of() : sent);
        Collections.reverse(mails);
        List<DevMail> out = new ArrayList<>();
        for (Mail m : mails.subList(0, Math.min(20, mails.size()))) {
            String text = m.getText();
            Matcher x = text == null ? null : LINK.matcher(text);
            String link = x != null && x.find() ? x.group() : null;
            out.add(new DevMail(String.join(",", m.getTo()), m.getSubject(), text, link, link == null ? null : x.group(1)));
        }
        return ApiResponse.of(out);
    }
}
