package pe.logyx.shipment.application;

import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ajustes de un envío con el precio final (acordado + aceptados) y las sugerencias para el transportista
 * (estadía por parada y diferencia de peso). Las sugerencias no se cobran solas: el transportista las propone.
 */
public record AdjustmentsOverview(BigDecimal agreedPrice, BigDecimal acceptedTotal, BigDecimal finalPrice,
                                  BigDecimal commissionRate, List<ShipmentAdjustment> adjustments,
                                  List<Suggestion> suggestions) {

    /** {@code stopId} solo en estadía; {@code detail}: texto corto ("Esperó 5 h 10 min en el recojo"). */
    public record Suggestion(pe.logyx.shipment.domain.model.AdjustmentType type, UUID stopId, BigDecimal amount,
                             String detail) {}
}
