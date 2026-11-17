package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.shared.outbox.Outboxed;
import pe.logyx.shipment.application.event.ShipmentStatusChanged;
import pe.logyx.shipment.domain.model.Shipment;

/**
 * F06: si se cancela un envío adjudicado, su solicitud (MATCHED) pasa a CANCELLED con el mismo motivo, para que la
 * PYME la republique. Llega por el outbox (ADR-0006) y corre en la transacción de sistema del despacho: quien cancela
 * puede ser el transportista, que bajo RLS no puede escribir la solicitud de la PYME. Idempotente.
 */
@ApplicationScoped
public class RequestLifecycleObserver {

    @Inject ShipmentRequestRepository requests;

    void onShipmentStatus(@Observes @Outboxed ShipmentStatusChanged e) {
        if (e.status() != Shipment.Status.CANCELLED || e.requestId() == null) return;
        requests.findById(e.requestId()).ifPresent(r -> {
            if (r.cancelAfterAward(e.reason())) requests.save(r);
        });
    }
}
