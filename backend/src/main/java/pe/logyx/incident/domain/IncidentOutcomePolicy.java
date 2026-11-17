package pe.logyx.incident.domain;

import pe.logyx.incident.domain.model.IncidentShipmentOutcome;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.Shipment;

import java.util.Map;
import java.util.Set;

/**
 * RF55: qué estado final puede fijar el operador según cómo está el envío al resolver la incidencia.
 * <pre>
 *   envío PENDING    → RESUME (sigue) | CANCELLED
 *   envío IN_TRANSIT → RESUME | DELIVERED (se dio por entregado) | CANCELLED (p. ej. carga extraviada)
 *   envío DELIVERED  → DELIVERED (reclamo posterior a la entrega: el envío queda como está)
 *   envío CANCELLED  → CANCELLED
 * </pre>
 * Falta o no corresponde: LOGYX-INC-006 ({@code shipmentOutcome}).
 */
public final class IncidentOutcomePolicy {

    private static final Map<Shipment.Status, Set<IncidentShipmentOutcome>> ALLOWED = Map.of(
            Shipment.Status.PENDING, Set.of(IncidentShipmentOutcome.RESUME, IncidentShipmentOutcome.CANCELLED),
            Shipment.Status.IN_TRANSIT, Set.of(IncidentShipmentOutcome.RESUME, IncidentShipmentOutcome.DELIVERED,
                    IncidentShipmentOutcome.CANCELLED),
            Shipment.Status.DELIVERED, Set.of(IncidentShipmentOutcome.DELIVERED),
            Shipment.Status.CANCELLED, Set.of(IncidentShipmentOutcome.CANCELLED));

    private IncidentOutcomePolicy() {}

    public static void ensureValid(IncidentShipmentOutcome outcome, Shipment.Status shipmentStatus) {
        if (outcome == null) {
            throw BusinessException.badRequest("LOGYX-INC-006",
                    "Indica el estado final del envío (RESUME, DELIVERED o CANCELLED)", "shipmentOutcome");
        }
        if (!ALLOWED.get(shipmentStatus).contains(outcome)) {
            throw BusinessException.conflict("LOGYX-INC-006", "Con el envío en " + shipmentStatus
                    + " el estado final puede ser: " + ALLOWED.get(shipmentStatus), "shipmentOutcome");
        }
    }
}
