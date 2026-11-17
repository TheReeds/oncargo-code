package pe.logyx.shipment.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import pe.logyx.shipment.domain.model.Shipment;

import java.util.UUID;

/**
 * Evento de dominio: el envío cambió de estado (salió del recojo, fue entregado o se canceló). {@code actorOrgId} y
 * {@code reason} solo vienen en cancelaciones y cierres del operador (V13; null en eventos anteriores).
 * {@code requestId} permite a marketplace cerrar la solicitud y a trip la reserva al cancelarse.
 */
public record ShipmentStatusChanged(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, Shipment.Status status,
                                    String route, UUID actorOrgId, String reason, UUID requestId) implements OutboxEvent {

    public ShipmentStatusChanged(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, Shipment.Status status,
                                 String route) {
        this(shipmentId, requesterOrgId, carrierOrgId, status, route, null, null, null);
    }
}
