package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.shipment.application.port.out.RequestInfoProvider;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;
import pe.logyx.shipment.domain.model.StopContact;

import java.util.List;
import java.util.UUID;

/** Genera el envío (recojo + entrega) a partir de la oferta aceptada. */
@ApplicationScoped
public class CreateShipmentUseCase {

    @Inject ShipmentRepository shipments;
    @Inject ShipmentStopRepository stops;
    @Inject FleetService fleet;
    @Inject RequestInfoProvider requests;

    /** Lo invoca marketplace (AcceptBidUseCase) dentro de la transacción de la PYME. */
    @Transactional(Transactional.TxType.MANDATORY)
    public UUID createFromAcceptedBid(CreateShipmentCommand cmd) {
        String plate = fleet.plateForContract(cmd.vehicleId());
        Shipment s = Shipment.contract(cmd.requestId(), cmd.bidId(), cmd.requesterOrgId(), cmd.carrierOrgId(),
                cmd.vehicleId(), plate, cmd.agreedPrice(), cmd.loadGroupId(), cmd.commissionRate());
        shipments.save(s);
        // RN07 post-adjudicación (roadmap 1.3): el contacto, la ventana y las instrucciones de cada extremo pasan de la
        // solicitud (privados hasta adjudicar) a las paradas del envío, que solo ven sus partes y el conductor asignado.
        var info = requests.byIds(List.of(cmd.requestId())).get(cmd.requestId());
        StopContact pickup = info == null ? StopContact.NONE : info.pickupContact();
        StopContact delivery = info == null ? StopContact.NONE : info.deliveryContact();
        stops.save(ShipmentStop.plan(s, 1, ShipmentStop.Type.PICKUP, cmd.originCity(), cmd.originAddress(), pickup));
        stops.save(ShipmentStop.plan(s, 2, ShipmentStop.Type.DELIVERY, cmd.destinationCity(), cmd.destinationAddress(),
                delivery));
        return s.id();
    }
}
