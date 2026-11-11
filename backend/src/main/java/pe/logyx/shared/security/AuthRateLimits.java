package pe.logyx.shared.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.shared.domain.BusinessException;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Rate limiting de login y registro (docs/security.md):
 * <ul>
 *   <li>login por IP de cliente: {@code logyx.rate-limit.login.per-ip} intentos por ventana;</li>
 *   <li>login por email: tras {@code logyx.rate-limit.login.failures-per-email} fallos en la ventana, la cuenta queda
 *       bloqueada hasta que la ventana expire (aunque la contraseña sea correcta). Un login correcto la reinicia;</li>
 *   <li>registro por IP: {@code logyx.rate-limit.register.per-ip} por ventana.</li>
 * </ul>
 * La IP sale de la conexión o, detrás de un proxy confiable ({@code quarkus.http.proxy.*}), de X-Forwarded-For
 * (el servidor Astro la reenvía). Responde 429 {@code LOGYX-AUTH-429}.
 */
@ApplicationScoped
public class AuthRateLimits {

    private final RateLimiter limiter = new RateLimiter(Clock.systemUTC());

    @ConfigProperty(name = "logyx.rate-limit.enabled", defaultValue = "true") boolean enabled;
    @ConfigProperty(name = "logyx.rate-limit.window", defaultValue = "PT15M") Duration window;
    @ConfigProperty(name = "logyx.rate-limit.login.per-ip", defaultValue = "50") int loginPerIp;
    @ConfigProperty(name = "logyx.rate-limit.login.failures-per-email", defaultValue = "10") int failuresPerEmail;
    @ConfigProperty(name = "logyx.rate-limit.register.per-ip", defaultValue = "10") int registerPerIp;

    public void beforeLogin(String ip, String email) {
        if (!enabled) return;
        String emailKey = emailKey(email);
        if (limiter.exhausted(emailKey, failuresPerEmail, window)) throw tooMany(limiter.retryAfterSeconds(emailKey, window));
        String ipKey = "login-ip:" + ip;
        if (!limiter.tryAcquire(ipKey, loginPerIp, window)) throw tooMany(limiter.retryAfterSeconds(ipKey, window));
    }

    public void loginFailed(String email) {
        if (enabled) limiter.record(emailKey(email), window);
    }

    public void loginSucceeded(String email) {
        if (enabled) limiter.reset(emailKey(email));
    }

    public void beforeRegister(String ip) {
        if (!enabled) return;
        String key = "register-ip:" + ip;
        if (!limiter.tryAcquire(key, registerPerIp, window)) throw tooMany(limiter.retryAfterSeconds(key, window));
    }

    private static String emailKey(String email) {
        return "login-email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private static BusinessException tooMany(long retryAfter) {
        return new TooManyRequests(retryAfter);
    }

    /** 429 con {@code Retry-After} (lo agrega {@code ExceptionMappers}). */
    public static final class TooManyRequests extends BusinessException {
        private final long retryAfterSeconds;

        TooManyRequests(long retryAfterSeconds) {
            super(429, "LOGYX-AUTH-429", "Demasiados intentos. Espera unos minutos e inténtalo de nuevo.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() { return retryAfterSeconds; }
    }
}
