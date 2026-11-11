package pe.logyx.shipment.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/**
 * Evento de dominio: el conductor llegó a una parada o la completó. Solo lo consume el tiempo real (ADR-0007);
 * {@code driverProfileId} es el usuario del conductor (para dirigirle el evento a él).
 */
public record ShipmentStopUpdated(UUID shipmentId, UUID stopId, UUID requesterOrgId, UUID carrierOrgId,
                                  UUID driverProfileId, String stopStatus, String shipmentStatus) implements OutboxEvent {}
