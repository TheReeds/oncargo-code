package pe.logyx.negotiation.domain.model;

import pe.logyx.shared.domain.ContactInfoPolicy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mensaje del hilo de chat de una solicitud con un transportista (RF25). El hilo se identifica por
 * (requestId, carrierOrgId). {@code system} = aviso automático (contra-ofertas).
 */
public record Message(UUID id, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, UUID senderOrgId,
                      UUID senderProfileId, String body, BigDecimal proposedPrice, boolean system,
                      OffsetDateTime createdAt) {

    /** Mensaje de una persona: RN07 se aplica aquí. */
    public static Message write(UUID requestId, UUID requesterOrgId, UUID carrierOrgId, UUID senderOrgId,
                                UUID senderProfileId, String body) {
        ContactInfoPolicy.ensureNoContactInfo(body, "body");
        return new Message(UUID.randomUUID(), requestId, requesterOrgId, carrierOrgId, senderOrgId, senderProfileId,
                body.trim(), null, false, OffsetDateTime.now());
    }

    public static Message system(UUID requestId, UUID requesterOrgId, UUID carrierOrgId, UUID senderOrgId,
                                 String body, BigDecimal proposedPrice) {
        return new Message(UUID.randomUUID(), requestId, requesterOrgId, carrierOrgId, senderOrgId, null, body,
                proposedPrice, true, OffsetDateTime.now());
    }
}
