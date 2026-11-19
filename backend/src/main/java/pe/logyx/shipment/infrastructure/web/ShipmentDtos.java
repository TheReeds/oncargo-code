package pe.logyx.shipment.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.logyx.catalog.domain.City;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shared.domain.TimeOfDay;
import pe.logyx.shipment.domain.model.ShipmentStop;
import pe.logyx.shipment.domain.model.StopContact;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    /** {@code reason}: obligatorio si la carga ya se recogió (relevo en ruta, LOGYX-SHP-006). */
    public record AssignDriver(@NotNull UUID driverId, @Size(max = 500) String reason) {}

    /** Cambio de vehículo (F06). {@code reason}: obligatorio si la carga ya se recogió. */
    public record ChangeVehicle(@NotNull UUID vehicleId, @Size(max = 500) String reason) {}

    /** Cancelación con motivo (F06). */
    public record CancelShipment(@NotBlank(message = "Indica el motivo") @Size(max = 500) String reason) {}

    /**
     * Cuerpo opcional de «Llegué» / «Carga recogida» / «Entregado» (roadmap 1.11). {@code occurredAt}: hora del
     * dispositivo en que ocurrió (ISO-8601 con zona). Sin cuerpo o sin el campo vale la hora del servidor.
     */
    public record StopEvent(OffsetDateTime occurredAt,
                            /** Recojo (2.5): peso medido en kg (obligatorio si la PYME lo pidió, LOGYX-TRK-010). */
                            BigDecimal weightKg,
                            /** Entrega (2.5): nombre de quien recibe. */
                            @Size(max = 120) String receivedByName,
                            /** Entrega (2.5): DNI (8 dígitos) o carné de extranjería de quien recibe (LOGYX-TRK-011). */
                            @Size(max = 20) String receivedByDoc) {
        public StopEvent(OffsetDateTime occurredAt) { this(occurredAt, null, null, null); }

        pe.logyx.shipment.application.StopCompletion completion() {
            return new pe.logyx.shipment.application.StopCompletion(occurredAt, weightKg, receivedByName, receivedByDoc);
        }
    }

    /**
     * Registro de una parada desde la oficina del transportista (2.5): motivo obligatorio («celular sin batería»,
     * 3–300). Los demás campos, como {@link StopEvent}.
     */
    public record OfficeStopEvent(@NotBlank(message = "Indica el motivo") @Size(max = 300) String reason,
                                  OffsetDateTime occurredAt, BigDecimal weightKg,
                                  @Size(max = 120) String receivedByName, @Size(max = 20) String receivedByDoc) {
        pe.logyx.shipment.application.StopCompletion completion() {
            return new pe.logyx.shipment.application.StopCompletion(occurredAt, weightKg, receivedByName, receivedByDoc);
        }
    }

    /** Lo que la PYME pide en el recojo (2.5): foto de la carga y peso medido. */
    public record PickupRequirementsRequest(boolean photoRequired, boolean weightRequired) {}

    /**
     * {@code arrivedAt}/{@code completedAt}: cuándo ocurrió (hora del dispositivo, acotada); {@code *RecordedAt}: cuándo
     * lo recibió el servidor. Contacto, ventana ({@code HH:mm}) e instrucciones: RN07 post-adjudicación, solo para las
     * partes, el conductor asignado y el operador (null para cualquier otro).
     */
    public record StopView(UUID id, int sequence, ShipmentStop.Type type, City city, String address,
                           ShipmentStop.Status status, OffsetDateTime arrivedAt, OffsetDateTime completedAt,
                           OffsetDateTime arrivedRecordedAt, OffsetDateTime completedRecordedAt,
                           String contactName, String contactPhone, String windowFrom, String windowTo,
                           String instructions,
                           /** Recojo (2.5): peso medido al recoger. */
                           BigDecimal loadedWeightKg,
                           /** Entrega (2.5): quién recibió (solo las partes, el conductor asignado y el operador). */
                           String receivedByName, String receivedByDoc,
                           /** 2.5: motivo si la oficina registró la llegada o el cierre en lugar del conductor. */
                           String arrivalOfficeReason, String completionOfficeReason) {
        static StopView of(ShipmentStop s, boolean withContact) {
            StopContact c = withContact ? s.contact() : StopContact.NONE;
            return new StopView(s.id(), s.sequence(), s.type(), s.city(), s.address(), s.status(),
                    s.arrivedAt(), s.completedAt(), s.arrivedRecordedAt(), s.completedRecordedAt(),
                    c.name(), c.phone(), TimeOfDay.format(c.windowFrom()), TimeOfDay.format(c.windowTo()),
                    c.instructions(), s.loadedWeightKg(), withContact ? s.receivedByName() : null,
                    withContact ? s.receivedByDoc() : null, s.arrivalOfficeReason(), s.completionOfficeReason());
        }
    }

    public record ShipmentView(UUID id, UUID requestId, String requesterName, String carrierName,
                               City originCity, City destinationCity, String cargoDescription,
                               BigDecimal weightKg, BigDecimal agreedPrice, String vehiclePlate,
                               UUID driverId, String driverName, Shipment.Status status,
                               OffsetDateTime createdAt, OffsetDateTime deliveredAt,
                               List<StopView> stops,
                               /** RN11: hay una incidencia activa; el conductor no puede avanzar paradas. */
                               boolean hasActiveIncident,
                               /** Fecha de recojo de la solicitud. */
                               LocalDate pickupDate,
                               /** Vehículo del contrato (el transportista puede cambiarlo, F06). */
                               UUID vehicleId,
                               /** Viaje o plan Smart Load que consolida este envío con otros en el mismo vehículo. */
                               UUID loadGroupId,
                               /** Motivo, fecha y organización que canceló (solo si CANCELLED con motivo). */
                               String cancellationReason, OffsetDateTime cancelledAt, UUID cancelledByOrgId,
                               /** Precio adjudicado con IGV desglosado; el transportista y el operador ven además la comisión (ADR-0010). */
                               PriceBreakdownView priceBreakdown,
                               /** Código legible heredado de la solicitud (OC-XXXXX). */
                               String code,
                               /** Referencia propia de la PYME (OC o pedido); solo las partes la ven. */
                               String customerReference,
                               /** Organizaciones de las partes (enlaces del operador). */
                               UUID requesterOrgId, UUID carrierOrgId,
                               /** 2.5: lo que la PYME pide en el recojo. */
                               boolean pickupPhotoRequired, boolean pickupWeightRequired,
                               /** 2.6: suma de los ajustes aceptados (0 si no hay) y cuántos esperan respuesta. */
                               BigDecimal adjustmentsTotal, long pendingAdjustments,
                               /** 2.6: precio final = acordado + ajustes aceptados. */
                               BigDecimal finalPrice,
                               /** 2.6: desglose del precio final (null si no hay ajustes aceptados). */
                               PriceBreakdownView finalPriceBreakdown) {
        static ShipmentView of(ShipmentSummary sum) {
            Shipment s = sum.shipment();
            var r = sum.request();
            boolean contacts = sum.contactsVisible();
            return new ShipmentView(s.id(), s.requestId(), sum.requesterName(), sum.carrierName(),
                    r.originCity(), r.destinationCity(), r.cargoDescription(), r.weightKg(), s.agreedPrice(),
                    s.vehiclePlate(), s.driverId(), s.driverName(), s.status(), s.createdAt(), s.deliveredAt(),
                    sum.stops().stream().map(st -> StopView.of(st, contacts)).toList(), sum.activeIncident(),
                    r == null ? null : r.pickupDate(), s.vehicleId(), s.loadGroupId(),
                    s.cancellation() == null ? null : s.cancellation().reason(),
                    s.cancellation() == null ? null : s.cancellation().at(),
                    s.cancellation() == null ? null : s.cancellation().byOrgId(),
                    PriceBreakdownView.of(s.agreedPrice(), s.commissionRate()),
                    r == null ? null : r.code(), contacts && r != null ? r.customerReference() : null,
                    s.requesterOrgId(), s.carrierOrgId(), s.pickupRequirements().photo(),
                    s.pickupRequirements().weight(), sum.adjustmentsTotal(), sum.pendingAdjustments(),
                    sum.finalPrice(), sum.adjustmentsTotal().signum() == 0 ? null
                            : PriceBreakdownView.of(sum.finalPrice(), s.commissionRate()));
        }

        static List<ShipmentView> of(List<ShipmentSummary> list) {
            return list.stream().map(ShipmentView::of).toList();
        }
    }
}
