package pe.logyx.shipment.application;

import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateShipmentCommand(UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId,
                                    UUID vehicleId, BigDecimal agreedPrice,
                                    City originCity, String originAddress,
                                    City destinationCity, String destinationAddress,
                                    /** Viaje o plan Smart Load que consolida envíos en el mismo vehículo (o null). */
                                    UUID loadGroupId,
                                    /** Comisión vigente al adjudicar (nula = sin registrar). */
                                    BigDecimal commissionRate) {
    public CreateShipmentCommand(UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
                                 BigDecimal agreedPrice, City originCity, String originAddress, City destinationCity,
                                 String destinationAddress, UUID loadGroupId) {
        this(requestId, bidId, requesterOrgId, carrierOrgId, vehicleId, agreedPrice, originCity, originAddress,
                destinationCity, destinationAddress, loadGroupId, null);
    }
}
