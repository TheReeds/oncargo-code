package pe.logyx.identity.application;

import io.quarkus.logging.Log;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.identity.application.port.out.AccountMailer;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.application.port.out.PasswordHasher;
import pe.logyx.identity.application.port.out.PasswordTokenRepository;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.OpaqueTokens;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.PasswordToken;
import pe.logyx.identity.domain.model.PasswordTokenPurpose;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tokens de un solo uso para definir contraseña (F02, F03; ADR-0009): "olvidé mi contraseña" ({@code RESET}) e
 * invitaciones de un ADMIN ({@code INVITE}). Solo se guarda el SHA-256; el token en claro solo va en el enlace del
 * correo ({@code logyx.password-reset.url?token=…}). Todo por {@code SystemTx}: no hay usuario autenticado y
 * {@code password_tokens} no es accesible para logyx_app (V14).
 */
@ApplicationScoped
public class PasswordTokenService {

    /** Datos que muestra la pantalla de restablecer/aceptar invitación antes de pedir la contraseña. */
    public record TokenInfo(PasswordTokenPurpose purpose, OffsetDateTime expiresAt, String maskedEmail,
                            String organizationName) {}

    /** Resultado de canjear un token (para auditar y para la respuesta). */
    public record Redeemed(PasswordTokenPurpose purpose, String email) {}

    @Inject SystemTx system;
    @Inject PasswordTokenRepository tokens;
    @Inject ProfileRepository profiles;
    @Inject OrganizationRepository organizations;
    @Inject PasswordHasher passwords;
    @Inject AccountMailer mailer;
    @Inject AccountSessions sessions;
    @Inject AuditService audit;

    @ConfigProperty(name = "logyx.password-reset.ttl", defaultValue = "PT30M") Duration resetTtl;
    @ConfigProperty(name = "logyx.invitation.ttl", defaultValue = "PT72H") Duration inviteTtl;
    @ConfigProperty(name = "logyx.password-reset.url") String resetUrl;

    /** Pocos hilos y cola acotada: el trabajo de "olvidé mi contraseña" no compite con las peticiones. */
    private final ExecutorService worker = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500), daemonThreads());

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }

    // ------------------------------------------------------------ Olvidé mi contraseña

    /**
     * Encola el trabajo real (buscar el perfil, emitir el token y enviar el correo) fuera del hilo de la respuesta:
     * el tiempo de respuesta no revela si el email existe. Si la cola está llena, se descarta (se loguea).
     */
    public void requestResetAsync(String email) {
        try {
            worker.execute(() -> {
                try {
                    requestReset(email);
                } catch (RuntimeException e) {
                    Log.errorf("No se pudo procesar un pedido de restablecimiento: %s", e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            Log.warn("Cola de restablecimiento llena: pedido descartado");
        }
    }

    /**
     * Si el email es de un usuario activo, emite un token RESET (o reenvía la invitación si aún no la aceptó) y envía
     * el correo. Si no existe o está desactivado, no hace nada. Síncrono: lo usa {@link #requestResetAsync} y los tests.
     */
    public void requestReset(String email) {
        record Mail(Profile profile, Organization org, String raw, PasswordTokenPurpose purpose, String invitedBy) {}
        // SystemTx: sin usuario; busca el email en todas las organizaciones y escribe en password_tokens.
        Mail mail = system.call(() -> {
            Profile p = profiles.findByEmail(email).filter(Profile::active).orElse(null);
            if (p == null) return null;
            Organization org = organizations.findById(p.organizationId()).orElseThrow();
            PasswordTokenPurpose purpose = p.invitationPending() ? PasswordTokenPurpose.INVITE
                    : PasswordTokenPurpose.RESET;
            String raw = issue(p, purpose);
            audit.record(p.id(), p.organizationId(), purpose == PasswordTokenPurpose.RESET
                    ? "PASSWORD_RESET_REQUESTED" : "USER_INVITATION_RESENT", "profile", p.id(), null);
            String invitedBy = p.invitedBy() == null ? null
                    : profiles.findById(p.invitedBy()).map(Profile::fullName).orElse(null);
            return new Mail(p, org, raw, purpose, invitedBy);
        });
        if (mail == null) return;
        if (mail.purpose() == PasswordTokenPurpose.RESET) {
            mailer.sendPasswordReset(mail.profile().email(), mail.profile().fullName(), link(mail.raw()), resetTtl);
        } else {
            mailer.sendInvitation(mail.profile().email(), mail.profile().fullName(), mail.org().legalName(),
                    mail.invitedBy(), link(mail.raw()), inviteTtl);
        }
    }

    // ------------------------------------------------------------ Invitaciones (las pide OrganizationUserService)

    /**
     * Emite un token INVITE (invalida los anteriores) y devuelve el enlace. Dentro del {@code SystemTx} que creó el
     * perfil se une a él; el correo se envía después de confirmar con {@link #mailInvitation}.
     */
    public String issueInvitation(Profile invited) {
        return link(system.call(() -> issue(invited, PasswordTokenPurpose.INVITE)));
    }

    public void mailInvitation(Profile invited, String organizationName, String invitedByName, String link) {
        mailer.sendInvitation(invited.email(), invited.fullName(), organizationName, invitedByName, link, inviteTtl);
    }

    /** Emite el token (invalida los anteriores sin usar). Requiere un SystemTx activo. */
    String issue(Profile p, PasswordTokenPurpose purpose) {
        OffsetDateTime now = OffsetDateTime.now();
        tokens.revokeUnused(p.id(), now);
        String raw = OpaqueTokens.generate();
        tokens.insert(PasswordToken.issue(p.id(), purpose, OpaqueTokens.hash(raw), now,
                purpose == PasswordTokenPurpose.RESET ? resetTtl : inviteTtl));
        return raw;
    }

    // ------------------------------------------------------------ Canje

    /** Para la pantalla: propósito, vencimiento y email enmascarado. 400 LOGYX-AUTH-005 si no vale. */
    // SystemTx: sin usuario; password_tokens solo es accesible para la plataforma.
    public TokenInfo inspect(String raw) {
        if (!OpaqueTokens.wellFormed(raw)) throw invalidToken();
        TokenInfo info = system.call(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            PasswordToken t = tokens.findByHash(OpaqueTokens.hash(raw)).filter(x -> x.usable(now)).orElse(null);
            if (t == null) return null;
            Profile p = profiles.findById(t.profileId()).filter(Profile::active).orElse(null);
            if (p == null) return null;
            String org = organizations.findById(p.organizationId()).map(Organization::legalName).orElse(null);
            return new TokenInfo(t.purpose(), t.expiresAt(), maskEmail(p.email()), org);
        });
        if (info == null) throw invalidToken();
        return info;
    }

    /**
     * Define la contraseña con el token: lo marca usado, invalida los demás del usuario, sube la versión de sesión
     * (cierra todas sus sesiones) y audita. Una invitación exige aceptar la política de privacidad (Ley N° 29733).
     */
    // SystemTx: sin usuario; password_tokens solo es accesible para la plataforma.
    public Redeemed redeem(String raw, String newPassword, boolean acceptPrivacyPolicy) {
        if (!OpaqueTokens.wellFormed(raw)) throw invalidToken();
        String hash = passwords.hash(newPassword);
        Redeemed r = system.call(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            PasswordToken t = tokens.findByHashForUpdate(OpaqueTokens.hash(raw)).filter(x -> x.usable(now)).orElse(null);
            if (t == null) return null;
            Profile p = profiles.findById(t.profileId()).filter(Profile::active).orElse(null);
            if (p == null) return null;
            if (t.purpose() == PasswordTokenPurpose.INVITE) {
                if (!acceptPrivacyPolicy) {
                    throw BusinessException.badRequest("LOGYX-AUTH-011",
                            "Para activar tu cuenta debes aceptar la política de privacidad (Ley N° 29733)",
                            "acceptPrivacyPolicy");
                }
                p.acceptInvitation(hash, now);
            } else {
                p.changePassword(hash, now);
            }
            profiles.update(p);
            t.markUsed(now);
            tokens.update(t);
            tokens.revokeUnused(p.id(), now);
            audit.record(p.id(), p.organizationId(), t.purpose() == PasswordTokenPurpose.INVITE
                    ? "USER_INVITATION_ACCEPTED" : "PASSWORD_RESET_COMPLETED", "profile", p.id(), null);
            sessions.revoked(p.id());
            return new Redeemed(t.purpose(), p.email());
        });
        if (r == null) throw invalidToken();
        return r;
    }

    private String link(String raw) {
        String sep = resetUrl.contains("?") ? "&" : "?";
        return resetUrl + sep + "token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    /** {@code juan.perez@empresa.pe} → {@code ju•••@empresa.pe}. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "•••";
        String local = email.substring(0, at);
        String shown = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return shown + "•••" + email.substring(at);
    }

    private static BusinessException invalidToken() {
        return BusinessException.badRequest("LOGYX-AUTH-005",
                "El enlace no es válido o ya venció. Pide uno nuevo desde «¿Olvidaste tu contraseña?».", "token");
    }

    private static java.util.concurrent.ThreadFactory daemonThreads() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "logyx-account-mail-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

}
