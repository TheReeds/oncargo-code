package pe.logyx.negotiation.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.negotiation.domain.model.Message;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "sender_org_id")
    public UUID senderOrgId;

    @Column(name = "sender_profile_id")
    public UUID senderProfileId;

    public String body;

    @Column(name = "proposed_price")
    public BigDecimal proposedPrice;

    @Column(name = "is_system")
    public boolean system;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Message toDomain() {
        return new Message(id, requestId, requesterOrgId, carrierOrgId, senderOrgId, senderProfileId, body,
                proposedPrice, system, createdAt);
    }

    static MessageEntity from(Message m) {
        MessageEntity e = new MessageEntity();
        e.id = m.id();
        e.requestId = m.requestId();
        e.requesterOrgId = m.requesterOrgId();
        e.carrierOrgId = m.carrierOrgId();
        e.senderOrgId = m.senderOrgId();
        e.senderProfileId = m.senderProfileId();
        e.body = m.body();
        e.proposedPrice = m.proposedPrice();
        e.system = m.system();
        e.createdAt = m.createdAt();
        return e;
    }
}
