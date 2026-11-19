package pe.logyx.reports.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lectura de envíos para reportes, SQL nativo <b>bajo RLS</b> (cada parte ve sus envíos; el operador todos por sus
 * políticas {@code *_operator}). Nunca por {@code SystemTx}.
 */
public interface ReportReader {

    /** Base de fechas: adjudicación (reportes) o entrega (liquidaciones del mes). */
    enum Basis { AWARDED, DELIVERED }

    /** Fila cruda; el servicio calcula precio final, IGV y comisión. */
    record Row(UUID shipmentId, String code, OffsetDateTime awardedAt, OffsetDateTime deliveredAt, String status,
               UUID requesterOrgId, String requesterName, UUID carrierOrgId, String carrierName, String originCity,
               String destinationCity, String cargoType, BigDecimal weightKg, BigDecimal distanceKm,
               BigDecimal suggestedPrice, BigDecimal agreedPrice, BigDecimal acceptedAdjustments,
               BigDecimal commissionRate, UUID vehicleId, String vehiclePlate, BigDecimal vehicleCapacityKg,
               String settlementStatus) {}

    /**
     * Envíos no cancelados entre {@code from} y {@code to} (fechas de Lima, inclusive) según {@code basis}; con
     * {@code organizationId}, solo los de esa organización (como PYME o transportista). Devuelve hasta {@code limit}.
     */
    List<Row> rows(Basis basis, LocalDate from, LocalDate to, UUID organizationId, int limit);

    /** Ofertas del transportista por ruta en el período (por fecha de la oferta): total y ganadas. */
    record BidStats(String originCity, String destinationCity, long bids, long won) {}

    List<BidStats> bidStats(UUID carrierOrgId, LocalDate from, LocalDate to);

    /** Razón social y RUC (la propia organización o, para el operador, cualquiera). */
    record OrgInfo(UUID id, String legalName, String ruc, String type) {}

    java.util.Optional<OrgInfo> organization(UUID id);
}
