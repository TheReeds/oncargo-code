package pe.logyx.shipment.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/**
 * Evento de dominio: el transportista asignó (o reasignó) el conductor de un envío. Solo lo consume el tiempo real
 * (ADR-0007): avisa al conductor nuevo, al anterior (si cambió) y a las dos organizaciones.
 */
public record DriverAssigned(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID driverProfileId,
                             UUID previousDriverProfileId) implements OutboxEvent {}
