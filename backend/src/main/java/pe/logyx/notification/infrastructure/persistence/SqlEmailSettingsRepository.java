package pe.logyx.notification.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.notification.application.port.out.EmailSettingsRepository;
import pe.logyx.notification.domain.model.EmailMode;
import pe.logyx.notification.domain.model.EmailSettings;

import java.sql.Time;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** {@code user_preferences} (V24) con SQL nativo y upsert. RLS por persona ({@code user_preferences_own}). */
@ApplicationScoped
public class SqlEmailSettingsRepository implements EmailSettingsRepository {

    @Inject EntityManager em;

    @Override
    public EmailSettings forProfile(UUID profileId) {
        return forProfiles(List.of(profileId)).getOrDefault(profileId, EmailSettings.DEFAULT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<UUID, EmailSettings> forProfiles(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) return Map.of();
        String array = profileIds.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
        List<Object[]> rows = em.createNativeQuery("SELECT profile_id, email_enabled, email_mode, digest_hour, quiet_from, "
                        + "quiet_to, unsubscribed_at FROM user_preferences WHERE profile_id = ANY(CAST(?1 AS uuid[]))")
                .setParameter(1, array).getResultList();
        Map<UUID, EmailSettings> out = new HashMap<>();
        for (Object[] r : rows) {
            out.put((UUID) r[0], new EmailSettings((Boolean) r[1], EmailMode.valueOf((String) r[2]),
                    ((Number) r[3]).intValue(), time(r[4]), time(r[5]), offset(r[6])));
        }
        return out;
    }

    @Override
    public void upsert(UUID profileId, UUID orgId, EmailSettings s) {
        em.createNativeQuery("INSERT INTO user_preferences (profile_id, org_id, email_enabled, email_mode, digest_hour, "
                        + "quiet_from, quiet_to, unsubscribed_at) VALUES (?1, ?2, ?3, ?4, ?5, CAST(?6 AS time), "
                        + "CAST(?7 AS time), CAST(?8 AS timestamptz)) ON CONFLICT (profile_id) DO UPDATE SET "
                        + "email_enabled = EXCLUDED.email_enabled, email_mode = EXCLUDED.email_mode, "
                        + "digest_hour = EXCLUDED.digest_hour, quiet_from = EXCLUDED.quiet_from, quiet_to = EXCLUDED.quiet_to, "
                        + "unsubscribed_at = EXCLUDED.unsubscribed_at, updated_at = now()")
                .setParameter(1, profileId).setParameter(2, orgId).setParameter(3, s.emailEnabled())
                .setParameter(4, s.mode().name()).setParameter(5, (short) s.digestHour())
                .setParameter(6, s.quietFrom() == null ? null : s.quietFrom().toString())
                .setParameter(7, s.quietTo() == null ? null : s.quietTo().toString())
                .setParameter(8, s.unsubscribedAt() == null ? null : s.unsubscribedAt().toString())
                .executeUpdate();
    }

    static LocalTime time(Object o) {
        if (o == null) return null;
        if (o instanceof LocalTime t) return t;
        if (o instanceof Time t) return t.toLocalTime();
        return LocalTime.parse(o.toString());
    }

    static OffsetDateTime offset(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime t) return t;
        if (o instanceof java.time.Instant i) return i.atOffset(java.time.ZoneOffset.UTC);
        if (o instanceof java.sql.Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        return OffsetDateTime.parse(o.toString());
    }
}
