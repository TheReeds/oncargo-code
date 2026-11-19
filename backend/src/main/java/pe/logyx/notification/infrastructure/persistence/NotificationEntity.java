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

    /** V16: destinatario (una fila por persona). */
    @Column(name = "profile_id")
    public UUID profileId;

    @Enumerated(EnumType.STRING)
    public NotificationType type;

    public String title;
    public String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type")
    public NotificationTarget relatedType;

    @Column(name = "related_id")
    public UUID relatedId;

    /** V16: es de algo que esta persona creó («Tuyo»). */
    public boolean mine;

    @Column(name = "read_at")
    public OffsetDateTime readAt;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Notification toDomain() {
        return new Notification(id, orgId, profileId, type, title, body, relatedType, relatedId, mine, readAt, createdAt);
    }

    static NotificationEntity of(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.id = n.id();
        e.orgId = n.orgId();
        e.profileId = n.profileId();
        e.type = n.type();
        e.title = n.title();
        e.body = n.body();
        e.relatedType = n.relatedType();
        e.relatedId = n.relatedId();
        e.mine = n.mine();
        e.readAt = n.readAt();
        e.createdAt = n.createdAt();
        return e;
    }
}
