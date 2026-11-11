package pe.logyx.reputation.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.UUID;

/** Evento de dominio: nueva calificación (recalcular el Trust Score y avisar a la organización calificada). */
public record ReviewSubmitted(UUID reviewId, UUID shipmentId, UUID reviewerOrgId, String reviewerName,
                              UUID reviewedOrgId, BigDecimal rating) implements OutboxEvent {}
