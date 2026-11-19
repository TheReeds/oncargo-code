package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.notification.application.port.out.EmailSettingsRepository;
import pe.logyx.notification.application.port.out.NotificationPreferenceRepository;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.domain.EmailDeliveryPolicy;
import pe.logyx.notification.domain.NotificationPreferencePolicy;
import pe.logyx.notification.domain.model.EmailMode;
import pe.logyx.notification.domain.model.EmailSettings;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centro de avisos de la persona actual (V16: RLS {@code notifications_own_*} por organización y perfil). Leer y
 * marcar como leído no afecta a los compañeros: cada uno tiene su copia.
 */
@ApplicationScoped
@Transactional
public class NotificationService {

    @Inject NotificationRepository notifications;
    @Inject NotificationPreferenceRepository preferences;
    @Inject EmailSettingsRepository emailSettings;
    @Inject TenantContext tenant;

    /** Preferencia efectiva de una categoría, con si su in-app está bloqueado. */
    public record CategoryPreference(NotificationCategory category, boolean inApp, boolean email, boolean inAppLocked) {}

    public Paged<Notification> list(boolean unreadOnly, boolean mineOnly, PageRequest page) {
        return notifications.listByProfile(tenant.profileId(), unreadOnly, mineOnly, page);
    }

    public long unreadCount() {
        return notifications.countUnread(tenant.profileId());
    }

    public Notification markRead(UUID id) {
        UUID me = tenant.profileId();
        Notification n = notifications.findOwned(id, me).orElseThrow(() -> BusinessException.notFound("Notificación"));
        if (!n.isRead()) {
            OffsetDateTime now = OffsetDateTime.now();
            notifications.markRead(id, me, now);
            n.markRead(now);
        }
        return n;
    }

    public int markAllRead() {
        return notifications.markAllRead(tenant.profileId(), OffsetDateTime.now());
    }

    /** Categorías que aplican a mi tipo de organización, con mis valores (o los por defecto). */
    public List<CategoryPreference> preferences() {
        var effective = NotificationPreferencePolicy.effective(preferences.forProfile(tenant.profileId()));
        return visible(effective, tenant.orgType());
    }

    /** Guarda las categorías enviadas (las demás no cambian). Cualquier rol, también Lector: es su propia cuenta. */
    public List<CategoryPreference> updatePreferences(Collection<NotificationPreference> changes) {
        OrgType type = tenant.orgType();
        for (NotificationPreference p : changes) {
            if (!p.category().appliesTo(type)) {
                throw BusinessException.badRequest("LOGYX-NTF-002", "La categoría «" + p.category().label()
                        + "» no aplica a tu tipo de cuenta.", "category");
            }
            NotificationPreferencePolicy.validate(p);
        }
        changes.forEach(p -> preferences.upsert(tenant.profileId(), tenant.orgId(), p));
        return preferences();
    }

    /** V24 (2.2): correo activo, inmediato o resumen diario y horario de silencio de la persona actual. */
    public EmailSettings emailSettings() {
        return emailSettings.forProfile(tenant.profileId());
    }

    /**
     * Cambia las preferencias de correo (cualquier rol, también Lector: es su propia cuenta). Volver a activar el correo
     * deshace la baja del enlace. {@code LOGYX-NTF-004} horario de silencio incompleto o de duración cero.
     */
    public EmailSettings updateEmailSettings(boolean emailEnabled, EmailMode mode, int digestHour, LocalTime quietFrom,
                                             LocalTime quietTo) {
        EmailSettings current = emailSettings.forProfile(tenant.profileId());
        EmailSettings next = new EmailSettings(emailEnabled, mode, digestHour, quietFrom, quietTo,
                emailEnabled ? null : current.unsubscribedAt());
        EmailDeliveryPolicy.validate(next);
        emailSettings.upsert(tenant.profileId(), tenant.orgId(), next);
        return next;
    }

    private static List<CategoryPreference> visible(Map<NotificationCategory, NotificationPreference> effective, OrgType type) {
        return Arrays.stream(NotificationCategory.values()).filter(c -> c.appliesTo(type))
                .map(c -> {
                    NotificationPreference p = effective.get(c);
                    return new CategoryPreference(c, p.inApp(), p.email(), NotificationPreferencePolicy.inAppLocked(c));
                }).toList();
    }
}
