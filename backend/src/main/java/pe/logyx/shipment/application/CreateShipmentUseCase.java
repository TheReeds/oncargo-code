package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.util.UUID;

/** Genera el envío (recojo + entrega) a partir de la oferta aceptada. */
@ApplicationScoped
public class CreateShipmentUseCase {

    @Inject ShipmentRepository shipments;
    @Inject ShipmentStopRepository stops;
    @Inject FleetService fleet;

    /** Lo invoca marketplace (AcceptBidUseCase) dentro de la transacción de la PYME. */
    @Transactional(Transactional.TxType.MANDATORY)
    public UUID createFromAcceptedBid(CreateShipmentCommand cmd) {
        String plate = fleet.plateForContract(cmd.vehicleId());
        Shipment s = Shipment.contract(cmd.requestId(), cmd.bidId(), cmd.requesterOrgId(), cmd.carrierOrgId(),
                cmd.vehicleId(), plate, cmd.agreedPrice(), cmd.loadGroupId());
        shipments.save(s);
        stops.save(ShipmentStop.plan(s, 1, ShipmentStop.Type.PICKUP, cmd.originCity(), cmd.originAddress()));
        stops.save(ShipmentStop.plan(s, 2, ShipmentStop.Type.DELIVERY, cmd.destinationCity(), cmd.destinationAddress()));
        return s.id();
    }
}
