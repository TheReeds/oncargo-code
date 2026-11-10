package pe.logyx.notification.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Notificación in-app para una organización (RF49/RF51). */
public final class Notification {

    private final UUID id;
    private final UUID orgId;
    private final NotificationType type;
    private final String title;
    private final String body;
    private final NotificationTarget relatedType;
    private final UUID relatedId;
    private OffsetDateTime readAt;
    private final OffsetDateTime createdAt;

    public Notification(UUID id, UUID orgId, NotificationType type, String title, String body, NotificationTarget relatedType,
                        UUID relatedId, OffsetDateTime readAt, OffsetDateTime createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public static Notification to(UUID orgId, NotificationType type, String title, String body, NotificationTarget relatedType,
                                  UUID relatedId) {
        return new Notification(UUID.randomUUID(), orgId, type, truncate(title, 200), truncate(body, 1000),
                relatedType, relatedId, null, OffsetDateTime.now());
    }

    public void markRead(OffsetDateTime now) {
        if (readAt == null) readAt = now;
    }

    public boolean isRead() { return readAt != null; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public UUID id() { return id; }
    public UUID orgId() { return orgId; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String body() { return body; }
    public NotificationTarget relatedType() { return relatedType; }
    public UUID relatedId() { return relatedId; }
    public OffsetDateTime readAt() { return readAt; }
    public OffsetDateTime createdAt() { return createdAt; }
}
