package pe.logyx.realtime.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.realtime.application.port.out.ShipmentDrivers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura puntual de solo lectura (envío → usuario del conductor) para dirigir eventos; corre en la transacción de
 * sistema del despacho del outbox. Consulta SQL en vez de pasar por shipment/fleet: ninguna {@code application}
 * expone esta relación sin usuario autenticado y es un dato de enrutamiento, no de negocio.
 */
@ApplicationScoped
public class SqlShipmentDrivers implements ShipmentDrivers {

    @Inject EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<UUID> driverProfileOf(UUID shipmentId) {
        List<UUID> r = em.createNativeQuery("SELECT d.profile_id FROM shipments s JOIN drivers d ON d.id = s.driver_id "
                + "WHERE s.id = ?1").setParameter(1, shipmentId).getResultList();
        return r.stream().findFirst();
    }
}
