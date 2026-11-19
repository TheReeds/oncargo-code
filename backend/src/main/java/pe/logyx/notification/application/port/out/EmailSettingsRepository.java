package pe.logyx.notification.application.port.out;

import pe.logyx.notification.domain.model.EmailSettings;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** {@code user_preferences} (V24). Sin fila = {@link EmailSettings#DEFAULT}. RLS por persona; en el despacho, SystemTx. */
public interface EmailSettingsRepository {
    EmailSettings forProfile(UUID profileId);
    /** Varias personas a la vez (despacho del outbox y cola de correo, SystemTx); solo trae las que tienen fila. */
    Map<UUID, EmailSettings> forProfiles(Collection<UUID> profileIds);
    void upsert(UUID profileId, UUID orgId, EmailSettings settings);
}
