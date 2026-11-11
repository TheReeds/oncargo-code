package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.model.Shipment;

import java.util.UUID;

/** Cancela un envío que aún no salió (PENDING). Lo invoca el módulo trip al cancelarse una reserva o un viaje. */
@ApplicationScoped
public class CancelShipmentUseCase {

    @Inject ShipmentRepository shipments;

    /** Cualquiera de las dos partes (RLS: shipments_parties). Si ya salió: 409 LOGYX-SHP-003. */
    @Transactional
    public void cancelPending(UUID shipmentId) {
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> BusinessException.notFound("Envío"));
        s.cancel();
        shipments.save(s);
    }

    /** true si el envío ya salió o se cerró (no admite cancelación). */
    @Transactional
    public boolean hasStarted(UUID shipmentId) {
        return shipments.findById(shipmentId).map(s -> s.status() != Shipment.Status.PENDING).orElse(false);
    }
}
