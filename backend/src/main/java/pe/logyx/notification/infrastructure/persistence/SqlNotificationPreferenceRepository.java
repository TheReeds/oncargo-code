package pe.logyx.notification.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.notification.application.port.out.NotificationPreferenceRepository;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** {@code notification_preferences} (V16) con SQL nativo: clave compuesta y upsert. RLS por persona. */
@ApplicationScoped
public class SqlNotificationPreferenceRepository implements NotificationPreferenceRepository {

    @Inject EntityManager em;

    @Override
    public Map<NotificationCategory, NotificationPreference> forProfile(UUID profileId) {
        return forProfiles(List.of(profileId)).getOrDefault(profileId, Map.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<UUID, Map<NotificationCategory, NotificationPreference>> forProfiles(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) return Map.of();
        String array = profileIds.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
        List<Object[]> rows = em.createNativeQuery("SELECT profile_id, category, in_app, email FROM notification_preferences "
                + "WHERE profile_id = ANY(CAST(?1 AS uuid[]))").setParameter(1, array).getResultList();
        Map<UUID, Map<NotificationCategory, NotificationPreference>> out = new HashMap<>();
        for (Object[] r : rows) {
            NotificationCategory c;
            try {
                c = NotificationCategory.valueOf((String) r[1]);
            } catch (IllegalArgumentException e) {
                continue;
            }
            out.computeIfAbsent((UUID) r[0], k -> new EnumMap<>(NotificationCategory.class))
                    .put(c, new NotificationPreference(c, (Boolean) r[2], (Boolean) r[3]));
        }
        return out;
    }

    @Override
    public void upsert(UUID profileId, UUID orgId, NotificationPreference p) {
        em.createNativeQuery("INSERT INTO notification_preferences (profile_id, org_id, category, in_app, email) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5) ON CONFLICT (profile_id, category) DO UPDATE "
                        + "SET in_app = EXCLUDED.in_app, email = EXCLUDED.email, updated_at = now()")
                .setParameter(1, profileId).setParameter(2, orgId).setParameter(3, p.category().name())
                .setParameter(4, p.inApp()).setParameter(5, p.email()).executeUpdate();
    }
}
