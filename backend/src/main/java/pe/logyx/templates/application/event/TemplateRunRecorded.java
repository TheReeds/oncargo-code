package pe.logyx.templates.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una ocurrencia de una programación terminó: avisa a quien creó la plantilla. {@code kind}: PUBLISHED, DRAFT o
 * FAILED. {@code requestId}/{@code requestCode} solo en PUBLISHED; {@code message}: motivo del fallo o aviso de pausa.
 */
public record TemplateRunRecorded(UUID organizationId, UUID profileId, UUID templateId, String templateName,
                                  String kind, LocalDate pickupDate, UUID requestId, String requestCode, String route,
                                  String message, boolean paused) implements OutboxEvent {}
