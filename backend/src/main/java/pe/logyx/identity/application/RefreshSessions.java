package pe.logyx.identity.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.application.port.out.PasswordTokenRepository;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.application.port.out.RefreshTokenRepository;
import pe.logyx.identity.application.port.out.TokenIssuer;
import pe.logyx.identity.domain.OpaqueTokens;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.identity.domain.model.RefreshToken;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Refresh tokens rotativos (opt-in del conductor/móvil, F24; ADR-0009). Todo por {@code SystemTx}: no hay usuario
 * autenticado (el access token pudo vencer) y {@code refresh_tokens} no es accesible para logyx_app.
 */
@ApplicationScoped
public class RefreshSessions {

    @Inject SystemTx system;
    @Inject RefreshTokenRepository refreshTokens;
    @Inject PasswordTokenRepository passwordTokens;
    @Inject ProfileRepository profiles;
    @Inject OrganizationRepository organizations;
    @Inject TokenIssuer tokens;
    @Inject AuditService audit;

    @ConfigProperty(name = "logyx.refresh.family-ttl", defaultValue = "P30D")
    Duration familyTtl;

    /** Familia nueva para un login con {@code refresh=true}. Devuelve el resultado con access de vida corta. */
    public AuthResult start(Profile p, Organization org, String userAgent) {
        String raw = OpaqueTokens.generate();
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken t = RefreshToken.startFamily(p.id(), OpaqueTokens.hash(raw), p.sessionVersion(), now, familyTtl,
                userAgent);
        // SystemTx: refresh_tokens solo lo escribe la plataforma (V14). Se une a la transacción de sistema del login.
        system.run(() -> refreshTokens.insert(t));
        Duration access = tokens.refreshAccessTtl();
        return new AuthResult(tokens.issue(p, org, access), access.toSeconds(), new UserAccount(p, org), raw,
                Duration.between(now, t.familyExpiresAt()).toSeconds());
    }

    /**
     * Rota: marca el token usado y emite access + refresh nuevos de la misma familia. Reutilizar uno ya usado revoca
     * la familia (robo). Rechaza vencido, revocado, perfil inactivo o versión de sesión cambiada: 401 LOGYX-AUTH-009.
     */
    // SystemTx: sin usuario (el access token pudo vencer); refresh_tokens solo es accesible para la plataforma.
    public AuthResult rotate(String raw) {
        if (!OpaqueTokens.wellFormed(raw)) throw invalid();
        record Outcome(AuthResult result, boolean reuse) {}
        Outcome out = system.call(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            RefreshToken t = refreshTokens.findByHashForUpdate(OpaqueTokens.hash(raw)).orElse(null);
            if (t == null) return new Outcome(null, false);
            if (t.used() && !t.revoked()) {
                refreshTokens.revokeFamily(t.familyId(), now);
                audit.record(t.profileId(), profiles.findById(t.profileId()).map(Profile::organizationId).orElse(null),
                        "REFRESH_TOKEN_REUSED", "profile", t.profileId(), "familia revocada");
                return new Outcome(null, true);
            }
            if (t.revoked() || t.used() || t.expired(now)) return new Outcome(null, false);
            Profile p = profiles.findById(t.profileId()).orElse(null);
            if (p == null || !p.canLogIn() || p.sessionVersion() != t.sessionVersion()) {
                refreshTokens.revokeFamily(t.familyId(), now);
                return new Outcome(null, false);
            }
            Organization org = organizations.findById(p.organizationId()).orElseThrow();
            t.markUsed(now);
            refreshTokens.update(t);
            String nextRaw = OpaqueTokens.generate();
            RefreshToken next = t.next(OpaqueTokens.hash(nextRaw), p.sessionVersion(), now);
            refreshTokens.insert(next);
            Duration access = tokens.refreshAccessTtl();
            return new Outcome(new AuthResult(tokens.issue(p, org, access), access.toSeconds(), new UserAccount(p, org),
                    nextRaw, Duration.between(now, next.familyExpiresAt()).toSeconds()), false);
        });
        if (out.reuse()) Log.warn("Refresh token reutilizado: familia revocada");
        if (out.result() == null) throw invalid();
        return out.result();
    }

    /** Cierra la sesión del dispositivo: revoca la familia del token (si existe). Idempotente. */
    // SystemTx: refresh_tokens solo es accesible para la plataforma.
    public void logout(String raw) {
        if (!OpaqueTokens.wellFormed(raw)) return;
        system.run(() -> refreshTokens.findByHashForUpdate(OpaqueTokens.hash(raw))
                .ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), OffsetDateTime.now())));
    }

    /** Limpieza periódica: tokens de contraseña y familias de refresh vencidos hace más de un día. */
    // SystemTx: job sin usuario sobre tablas de la plataforma.
    public int cleanup() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(1);
        return system.call(() -> refreshTokens.deleteExpiredBefore(cutoff) + passwordTokens.deleteExpiredBefore(cutoff));
    }

    private static BusinessException invalid() {
        return new BusinessException(401, "LOGYX-AUTH-009",
                "Tu sesión venció o se cerró en este dispositivo. Inicia sesión de nuevo.");
    }
}
