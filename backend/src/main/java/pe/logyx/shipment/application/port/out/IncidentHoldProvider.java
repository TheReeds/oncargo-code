package pe.logyx.shipment.application.port.out;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * RN11: qué envíos tienen una incidencia activa (OPEN o IN_REVIEW), que congela el avance de paradas.
 * Lo implementa {@code infrastructure/integration} con el módulo incident (incident ya depende de shipment).
 */
public interface IncidentHoldProvider {
    Set<UUID> withActiveIncident(Collection<UUID> shipmentIds);

    default boolean hasActiveIncident(UUID shipmentId) {
        return withActiveIncident(Set.of(shipmentId)).contains(shipmentId);
    }
}
