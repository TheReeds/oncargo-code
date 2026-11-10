package pe.logyx.negotiation.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.negotiation.application.port.out.MessageRepository;
import pe.logyx.negotiation.domain.model.Message;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheMessageRepository implements MessageRepository {

    @Override
    public Optional<Message> findInThread(UUID messageId, UUID requestId, UUID carrierOrgId) {
        return MessageEntity.<MessageEntity>find("id = ?1 and requestId = ?2 and carrierOrgId = ?3",
                messageId, requestId, carrierOrgId).firstResultOptional().map(MessageEntity::toDomain);
    }

    @Override
    public List<Message> listThread(UUID requestId, UUID carrierOrgId, OffsetDateTime beforeAt, UUID beforeId,
                                    int limit) {
        var q = beforeAt == null
                ? MessageEntity.<MessageEntity>find("requestId = ?1 and carrierOrgId = ?2 "
                        + "order by createdAt desc, id desc", requestId, carrierOrgId)
                : MessageEntity.<MessageEntity>find("requestId = ?1 and carrierOrgId = ?2 and "
                        + "(createdAt < ?3 or (createdAt = ?3 and id < ?4)) order by createdAt desc, id desc",
                        requestId, carrierOrgId, beforeAt, beforeId);
        return q.range(0, limit - 1).list().stream().map(MessageEntity::toDomain).toList();
    }

    @Override
    public long countThread(UUID requestId, UUID carrierOrgId) {
        return MessageEntity.count("requestId = ?1 and carrierOrgId = ?2", requestId, carrierOrgId);
    }

    @Override
    public List<ThreadStats> threads(UUID requestId) {
        List<Object[]> rows = MessageEntity.getEntityManager().createQuery(
                        "select m.carrierOrgId, count(m) from MessageEntity m where m.requestId = :r "
                                + "group by m.carrierOrgId", Object[].class)
                .setParameter("r", requestId).getResultList();
        return rows.stream().map(row -> {
            UUID carrier = (UUID) row[0];
            Message last = MessageEntity.<MessageEntity>find("requestId = ?1 and carrierOrgId = ?2 "
                    + "order by createdAt desc, id desc", requestId, carrier).firstResultOptional()
                    .map(MessageEntity::toDomain).orElse(null);
            return new ThreadStats(carrier, (Long) row[1], last);
        }).toList();
    }

    @Override
    public void insert(Message message) {
        MessageEntity.from(message).persist();
    }
}
