package pe.logyx.shipment.infrastructure.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.incident.application.IncidentQueries;
import pe.logyx.shipment.application.port.out.IncidentHoldProvider;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Adaptador del puerto RN11 sobre el módulo incident (lectura bajo la RLS del usuario actual). */
@ApplicationScoped
public class IncidentModuleHoldProvider implements IncidentHoldProvider {

    @Inject IncidentQueries incidents;

    @Override
    public Set<UUID> withActiveIncident(Collection<UUID> shipmentIds) {
        return incidents.shipmentsWithActiveIncident(shipmentIds);
    }
}
