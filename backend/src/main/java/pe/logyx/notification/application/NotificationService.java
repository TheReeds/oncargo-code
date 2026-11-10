package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Centro de notificaciones de la organización actual (RLS notifications_own_*). */
@ApplicationScoped
@Transactional
public class NotificationService {

    @Inject NotificationRepository notifications;
    @Inject TenantContext tenant;

    public Paged<Notification> list(boolean unreadOnly, PageRequest page) {
        return notifications.listByOrg(tenant.orgId(), unreadOnly, page);
    }

    public long unreadCount() {
        return notifications.countUnread(tenant.orgId());
    }

    public Notification markRead(UUID id) {
        Notification n = notifications.findOwned(id, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Notificación"));
        n.markRead(OffsetDateTime.now());
        notifications.save(n);
        return n;
    }

    public int markAllRead() {
        return notifications.markAllRead(tenant.orgId(), OffsetDateTime.now());
    }
}
