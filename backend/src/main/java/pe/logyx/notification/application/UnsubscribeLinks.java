package pe.logyx.notification.application;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.notification.domain.UnsubscribeToken;
import pe.logyx.notification.domain.model.NotificationCategory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Enlaces de baja de los correos de aviso (V24, 2.2): {@code <web>/baja?t=<token>} (página) y
 * {@code <web>/baja/one-click?t=<token>} (cabecera {@code List-Unsubscribe}, RFC 8058). La clave
 * {@code logyx.mail.unsubscribe-secret} (env {@code LOGYX_UNSUBSCRIBE_SECRET}) es obligatoria en producción y de al
 * menos 32 caracteres: el arranque falla sin ella (dev y test traen una fija).
 */
@Startup
@ApplicationScoped
public class UnsubscribeLinks {

    @ConfigProperty(name = "logyx.mail.unsubscribe-secret") String secret;
    @ConfigProperty(name = "logyx.web.url", defaultValue = "https://oncargo.tech") String webUrl;

    private byte[] key;

    @PostConstruct
    void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("logyx.mail.unsubscribe-secret (LOGYX_UNSUBSCRIBE_SECRET) debe tener al menos 32 caracteres");
        }
        key = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** {@code category == null}: baja de todo el correo de avisos. */
    public String token(UUID profileId, NotificationCategory category) {
        return UnsubscribeToken.sign(profileId, category, key);
    }

    public Optional<UnsubscribeToken.Claims> verify(String token) {
        return UnsubscribeToken.verify(token, key);
    }

    public String pageUrl(String token) { return base() + "/baja?t=" + token; }

    public String oneClickUrl(String token) { return base() + "/baja/one-click?t=" + token; }

    public String base() {
        return webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
    }
}
