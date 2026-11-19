package pe.logyx.notification.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Notificación in-app (RF49/RF51). Desde V16 cada aviso guardado es de <b>una persona</b> ({@code profileId}) con su
 * propio leído. Los observadores crean borradores con {@link #to} (toda la organización, {@code profileId == null}) o
 * {@link #toPerson} (una persona); {@code NotificationPublisher} expande el de organización en una copia por persona
 * activa con {@link #addressedTo} según sus preferencias. {@code mine} = es de algo que esa persona creó («Tuyo»).
 */
public final class Notification {

    private final UUID id;
    private final UUID orgId;
    private final UUID profileId;
    private final NotificationType type;
    private final String title;
    private final String body;
    private final NotificationTarget relatedType;
    private final UUID relatedId;
    private final boolean mine;
    private OffsetDateTime readAt;
    private final OffsetDateTime createdAt;

    public Notification(UUID id, UUID orgId, UUID profileId, NotificationType type, String title, String body,
                        NotificationTarget relatedType, UUID relatedId, boolean mine, OffsetDateTime readAt,
                        OffsetDateTime createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.profileId = profileId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.mine = mine;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    /** Borrador para toda la organización: se expande a cada persona al publicarse. */
    public static Notification to(UUID orgId, NotificationType type, String title, String body, NotificationTarget relatedType,
                                  UUID relatedId) {
        return new Notification(UUID.randomUUID(), Objects.requireNonNull(orgId), null, type, truncate(title, 200),
                truncate(body, 1000), relatedType, relatedId, false, null, OffsetDateTime.now());
    }

    /** Aviso para una persona concreta de la organización (sigue respetando sus preferencias). */
    public static Notification toPerson(UUID orgId, UUID profileId, NotificationType type, String title, String body,
                                        NotificationTarget relatedType, UUID relatedId) {
        return new Notification(UUID.randomUUID(), Objects.requireNonNull(orgId), Objects.requireNonNull(profileId), type,
                truncate(title, 200), truncate(body, 1000), relatedType, relatedId, true, null, OffsetDateTime.now());
    }

    /** Copia para una persona (id nuevo, sin leer). */
    public Notification addressedTo(UUID profile, boolean isMine) {
        return new Notification(UUID.randomUUID(), orgId, Objects.requireNonNull(profile), type, title, body, relatedType,
                relatedId, isMine, null, createdAt);
    }

    public boolean isForWholeOrganization() { return profileId == null; }

    public NotificationCategory category() { return NotificationCategory.of(type); }

    public void markRead(OffsetDateTime now) {
        if (readAt == null) readAt = now;
    }

    public boolean isRead() { return readAt != null; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public UUID id() { return id; }
    public UUID orgId() { return orgId; }
    public UUID profileId() { return profileId; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String body() { return body; }
    public NotificationTarget relatedType() { return relatedType; }
    public UUID relatedId() { return relatedId; }
    public boolean mine() { return mine; }
    public OffsetDateTime readAt() { return readAt; }
    public OffsetDateTime createdAt() { return createdAt; }
}
