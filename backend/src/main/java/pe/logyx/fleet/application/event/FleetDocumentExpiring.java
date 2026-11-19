package pe.logyx.fleet.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un documento de flota está por vencer o venció (2.4). {@code threshold}: 30, 7 o 0 días; {@code daysLeft} puede ser
 * negativo. {@code ownerType}: VEHICLE o DRIVER; {@code ownerLabel}: placa o nombre del conductor.
 */
public record FleetDocumentExpiring(UUID organizationId, String ownerType, UUID ownerId, String ownerLabel,
                                    String docType, String docLabel, LocalDate expiresAt, int daysLeft,
                                    int threshold) implements OutboxEvent {}
