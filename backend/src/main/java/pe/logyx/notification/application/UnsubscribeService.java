package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.notification.application.port.out.EmailSettingsRepository;
import pe.logyx.notification.application.port.out.NotificationPreferenceRepository;
import pe.logyx.notification.domain.NotificationPreferencePolicy;
import pe.logyx.notification.domain.UnsubscribeToken;
import pe.logyx.notification.domain.model.EmailSettings;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Baja del correo de avisos sin iniciar sesión (V24, 2.2; RFC 8058). El token ({@link UnsubscribeToken}) dice la
 * persona y el alcance: una categoría (apaga solo su correo; el aviso en la app sigue) o {@code ALL} (apaga todo el
 * correo de avisos: {@code user_preferences.email_enabled = false}). Inválido, alterado o de una persona desactivada:
 * misma respuesta 404 {@code LOGYX-NTF-003}. Los correos de cuenta (contraseña, invitaciones) no se ven afectados.
 */
@ApplicationScoped
public class UnsubscribeService {

    /** Lista blanca de la superficie pública: nada más sale de aquí. */
    public record Info(NotificationCategory category, String categoryLabel, String maskedEmail, boolean categoryEmail,
                       boolean emailEnabled) {}

    @Inject UnsubscribeLinks links;
    @Inject UserAccounts accounts;
    @Inject EmailSettingsRepository emailSettings;
    @Inject NotificationPreferenceRepository preferences;
    @Inject AuditService audit;
    @Inject SystemTx system;

    // SystemTx: sin usuario (el enlace del correo); lee y escribe las preferencias de esa persona y nada más.
    public Info inspect(String token) {
        var claims = links.verify(token).orElseThrow(UnsubscribeService::invalid);
        return system.call(() -> info(person(claims), claims));
    }

    /** {@code all = true} o un token de {@code ALL}: baja de todo el correo de avisos; si no, solo de la categoría. */
    // SystemTx: sin usuario; escribe user_preferences / notification_preferences del perfil firmado en el token.
    public Info unsubscribe(String token, boolean all) {
        var claims = links.verify(token).orElseThrow(UnsubscribeService::invalid);
        return system.call(() -> {
            Profile p = person(claims);
            OffsetDateTime now = OffsetDateTime.now();
            if (all || claims.all()) {
                emailSettings.upsert(p.id(), p.organizationId(), emailSettings.forProfile(p.id()).unsubscribed(now));
            } else {
                NotificationCategory c = claims.category();
                NotificationPreference current = NotificationPreferencePolicy.effective(preferences.forProfile(p.id())).get(c);
                preferences.upsert(p.id(), p.organizationId(), new NotificationPreference(c, current.inApp(), false));
            }
            audit.record(p.id(), p.organizationId(), "EMAIL_UNSUBSCRIBED", "profile", p.id(),
                    "enlace del correo: " + (all || claims.all() ? UnsubscribeToken.ALL : claims.scope()));
            return info(p, claims);
        });
    }

    private Profile person(UnsubscribeToken.Claims claims) {
        return accounts.byId(claims.profileId()).filter(Profile::active).filter(p -> !p.isDriver())
                .filter(p -> p.email() != null && !p.email().isBlank()).orElseThrow(UnsubscribeService::invalid);
    }

    private Info info(Profile p, UnsubscribeToken.Claims claims) {
        EmailSettings s = emailSettings.forProfile(p.id());
        NotificationCategory c = claims.category();
        boolean categoryEmail = c == null ? s.emailEnabled()
                : NotificationPreferencePolicy.effective(preferences.forProfile(p.id())).get(c).email();
        return new Info(c, Optional.ofNullable(c).map(NotificationCategory::label).orElse(null), mask(p.email()),
                categoryEmail, s.emailEnabled());
    }

    /** {@code juan.perez@empresa.pe} → {@code ju•••@empresa.pe}. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "•••";
        String local = email.substring(0, at);
        return (local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2)) + "•••" + email.substring(at);
    }

    static BusinessException invalid() {
        return new BusinessException(404, "LOGYX-NTF-003", "Este enlace de baja no es válido.");
    }
}
