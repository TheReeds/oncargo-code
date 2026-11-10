package pe.logyx.negotiation.application.event;

import java.util.UUID;

/** Evento de dominio: nuevo mensaje en un hilo; {@code recipientOrgId} es la otra parte. */
public record MessagePosted(UUID messageId, UUID requestId, UUID carrierOrgId, UUID senderOrgId, UUID recipientOrgId,
                            String senderName, String preview) {}
