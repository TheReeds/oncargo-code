package pe.logyx.shipment.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/**
 * El transportista cambió el conductor o el vehículo de un envío (F06). Se avisa a la PYME por el outbox.
 * {@code what}: {@code DRIVER} o {@code VEHICLE}. {@code reason} es obligatorio si la carga ya se recogió.
 */
public record ShipmentReassigned(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, String what,
                                 String vehiclePlate, String driverName, String reason, boolean afterPickup,
                                 String route) implements OutboxEvent {}
