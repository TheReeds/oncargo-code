package pe.logyx.shipment.application;

import pe.logyx.shipment.application.port.out.RequestInfoProvider.RequestInfo;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.util.List;

/**
 * Envío con sus paradas, nombres de las partes y datos de la carga. {@code activeIncident}: hay una incidencia
 * activa que congela el avance de paradas (RN11). {@code contactsVisible}: quien consulta puede ver el contacto, la
 * ventana y las instrucciones de las paradas (RN07 post-adjudicación: las partes, el conductor asignado y el operador).
 */
public record ShipmentSummary(Shipment shipment, List<ShipmentStop> stops, String requesterName,
                              String carrierName, RequestInfo request, boolean activeIncident,
                              boolean contactsVisible) {

    public ShipmentSummary(Shipment shipment, List<ShipmentStop> stops, String requesterName, String carrierName,
                           RequestInfo request, boolean activeIncident) {
        this(shipment, stops, requesterName, carrierName, request, activeIncident, false);
    }

    /** "Lima → Juliaca", para textos de notificaciones. */
    public String routeLabel() {
        return request == null ? "" : request.originCity().label + " → " + request.destinationCity().label;
    }

    public ShipmentStop stop(java.util.UUID stopId) {
        return stops.stream().filter(s -> s.id().equals(stopId)).findFirst()
                .orElseThrow(() -> pe.logyx.shared.domain.BusinessException.notFound("Parada"));
    }
}
