package pe.logyx.shipment.application;

import pe.logyx.shipment.application.port.out.RequestInfoProvider.RequestInfo;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.util.List;

/** Envío con sus paradas, nombres de las partes y datos de la carga. */
public record ShipmentSummary(Shipment shipment, List<ShipmentStop> stops, String requesterName,
                              String carrierName, RequestInfo request) {}
