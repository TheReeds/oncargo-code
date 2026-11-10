package pe.logyx.notification.infrastructure.persistence;

import pe.logyx.notification.domain.model.NotificationTarget;
import pe.logyx.notification.domain.model.NotificationType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.notification.domain.model.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "org_id")
    public UUID orgId;

    @Enumerated(EnumType.STRING)
    public NotificationType type;

    public String title;
    public String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type")
    public NotificationTarget relatedType;

    @Column(name = "related_id")
    public UUID relatedId;

    @Column(name = "read_at")
    public OffsetDateTime readAt;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Notification toDomain() {
        return new Notification(id, orgId, type, title, body, relatedType, relatedId, readAt, createdAt);
    }

    void apply(Notification n) {
        id = n.id();
        orgId = n.orgId();
        type = n.type();
        title = n.title();
        body = n.body();
        relatedType = n.relatedType();
        relatedId = n.relatedId();
        readAt = n.readAt();
        createdAt = n.createdAt();
    }
}
