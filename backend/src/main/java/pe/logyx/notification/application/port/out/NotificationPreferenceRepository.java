package pe.logyx.notification.application.port.out;

import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Preferencias guardadas (sin fila = valor por defecto de {@code NotificationPreferencePolicy}). */
public interface NotificationPreferenceRepository {
    Map<NotificationCategory, NotificationPreference> forProfile(UUID profileId);
    /** Varias personas a la vez (despacho del outbox, SystemTx). */
    Map<UUID, Map<NotificationCategory, NotificationPreference>> forProfiles(Collection<UUID> profileIds);
    void upsert(UUID profileId, UUID orgId, NotificationPreference preference);
}
