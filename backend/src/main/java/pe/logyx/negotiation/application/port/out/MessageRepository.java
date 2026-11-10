package pe.logyx.negotiation.application.port.out;

import pe.logyx.negotiation.domain.model.Message;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository {
    /** Resumen de un hilo: cantidad de mensajes y el último. */
    record ThreadStats(UUID carrierOrgId, long count, Message last) {}

    Optional<Message> findInThread(UUID messageId, UUID requestId, UUID carrierOrgId);
    /** Hasta {@code limit} mensajes anteriores a ({@code beforeAt}, {@code beforeId}) — o los últimos si null —, más recientes primero. */
    List<Message> listThread(UUID requestId, UUID carrierOrgId, OffsetDateTime beforeAt, UUID beforeId, int limit);
    long countThread(UUID requestId, UUID carrierOrgId);
    /** Hilos visibles (RLS) de la solicitud. */
    List<ThreadStats> threads(UUID requestId);
    void insert(Message message);
}
