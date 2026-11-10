package pe.logyx.shipment.application.event;

import pe.logyx.shipment.domain.model.Shipment;

import java.util.UUID;

/** Evento de dominio: el envío cambió de estado (salió del recojo o fue entregado). */
public record ShipmentStatusChanged(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, Shipment.Status status,
                                    String route) {}
