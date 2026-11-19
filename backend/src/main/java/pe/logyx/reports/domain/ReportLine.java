package pe.logyx.reports.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un envío (no cancelado) con sus montos ya calculados para los reportes (2.11). {@code finalPrice} = acordado + ajustes
 * aceptados, con IGV; {@code commission}/{@code commissionVat}/{@code carrierNet} con la tasa guardada en el envío
 * (ADR-0010, ADR-0012). {@code vehicleCapacityKg} nulo si quien consulta no ve la flota (la PYME).
 */
public record ReportLine(UUID shipmentId, String code, LocalDate awardedOn, LocalDate deliveredOn, String status,
                         UUID requesterOrgId, String requesterName, UUID carrierOrgId, String carrierName,
                         String originCity, String destinationCity, String cargoType, BigDecimal weightKg,
                         BigDecimal distanceKm, BigDecimal suggestedPrice, BigDecimal agreedPrice,
                         BigDecimal adjustmentsTotal, BigDecimal finalPrice, BigDecimal netOfVat, BigDecimal vat,
                         BigDecimal commissionRate, BigDecimal commission, BigDecimal commissionVat, BigDecimal carrierNet,
                         UUID vehicleId, String vehiclePlate, BigDecimal vehicleCapacityKg, String settlementStatus) {

    public String route() { return originCity + "-" + destinationCity; }

    /** Ahorro frente al sugerido del motor de costos (lo que la subasta bajó el precio); puede ser negativo. */
    public BigDecimal savings() { return suggestedPrice.subtract(agreedPrice); }
}
