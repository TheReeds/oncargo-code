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
 *   <li>registro por IP: {@code logyx.rate-limit.register.per-ip} por ventana;</li>
 *   <li>"olvidé mi contraseña" por IP ({@code forgot.per-ip}, 429) y por email ({@code forgot.per-email}: si se
 *       excede la respuesta es la misma, 202, pero no se envía correo; así no se revela si el email existe);</li>
 *   <li>canje de tokens de contraseña por IP ({@code reset.per-ip}) y refresh por IP ({@code refresh.per-ip});</li>
 *   <li>cotizador público por IP ({@code public-quote.per-ip}, 30 por ventana);</li>
 *   <li>seguimiento público ({@code /public/tracking/{token}}) por IP ({@code public-tracking.per-ip}, 120 por ventana:
 *       la página consulta cada 60 s);</li>
 *   <li>cambio de contraseña: tras {@code change-password.failures-per-user} contraseñas actuales incorrectas, 429.</li>
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
    @ConfigProperty(name = "logyx.rate-limit.forgot.per-ip", defaultValue = "20") int forgotPerIp;
    @ConfigProperty(name = "logyx.rate-limit.forgot.per-email", defaultValue = "3") int forgotPerEmail;
    @ConfigProperty(name = "logyx.rate-limit.reset.per-ip", defaultValue = "30") int resetPerIp;
    @ConfigProperty(name = "logyx.rate-limit.refresh.per-ip", defaultValue = "300") int refreshPerIp;
    @ConfigProperty(name = "logyx.rate-limit.public-quote.per-ip", defaultValue = "30") int publicQuotePerIp;
    @ConfigProperty(name = "logyx.rate-limit.public-tracking.per-ip", defaultValue = "120") int publicTrackingPerIp;
    @ConfigProperty(name = "logyx.rate-limit.change-password.failures-per-user", defaultValue = "10")
    int changePasswordFailures;

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

    /** 429 si la IP pidió demasiados restablecimientos (no depende del email: no revela nada). */
    public void beforeForgotPassword(String ip) {
        acquireOr429("forgot-ip:" + ip, forgotPerIp);
    }

    /** false si ese email ya pidió {@code forgot.per-email} enlaces en la ventana: se responde igual, sin enviar. */
    public boolean forgotPasswordEmailAllowed(String email) {
        if (!enabled) return true;
        return limiter.tryAcquire("forgot-email:" + normalize(email), forgotPerEmail, window);
    }

    public void beforePasswordTokenUse(String ip) {
        acquireOr429("reset-ip:" + ip, resetPerIp);
    }

    /** Cotizador público (sin cuenta): por IP, {@code logyx.rate-limit.public-quote.per-ip} por ventana → 429. */
    public void beforePublicQuote(String ip) {
        acquireOr429("public-quote-ip:" + ip, publicQuotePerIp);
    }

    /** Enlace público de seguimiento (sin cuenta, ADR-0011): por IP → 429. Cuenta también los tokens inválidos. */
    public void beforePublicTracking(String ip) {
        acquireOr429("public-tracking-ip:" + ip, publicTrackingPerIp);
    }

    public void beforeRefresh(String ip) {
        acquireOr429("refresh-ip:" + ip, refreshPerIp);
    }

    public void beforeChangePassword(String profileId) {
        if (!enabled) return;
        String key = "change-pw:" + profileId;
        if (limiter.exhausted(key, changePasswordFailures, window)) throw tooMany(limiter.retryAfterSeconds(key, window));
    }

    public void changePasswordFailed(String profileId) {
        if (enabled) limiter.record("change-pw:" + profileId, window);
    }

    private void acquireOr429(String key, int max) {
        if (!enabled) return;
        if (!limiter.tryAcquire(key, max, window)) throw tooMany(limiter.retryAfterSeconds(key, window));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
