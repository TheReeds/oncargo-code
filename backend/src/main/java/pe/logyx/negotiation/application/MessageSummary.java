package pe.logyx.negotiation.application;

import pe.logyx.negotiation.domain.model.Message;

/** Mensaje con el nombre de la organización que lo envió y si es del usuario actual. */
public record MessageSummary(Message message, String senderName, boolean mine) {}
