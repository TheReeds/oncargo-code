package pe.logyx.marketplace.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.AcceptOutcome;
import pe.logyx.marketplace.application.BidStanding;
import pe.logyx.marketplace.application.BidSummary;
import pe.logyx.marketplace.domain.BidStandingBand;
import pe.logyx.marketplace.application.PlaceBidCommand;
import pe.logyx.marketplace.application.PublishRequestCommand;
import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.marketplace.domain.VehicleRequirementPolicy;
import pe.logyx.fleet.domain.VehicleCargoCompatibility;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import pe.logyx.shared.domain.TimeOfDay;
import java.util.List;
import java.util.UUID;

public final class MarketplaceDtos {
    private MarketplaceDtos() {}

    public record CreateRequest(
            @NotBlank String originCity,
            @NotBlank @Size(max = 200) String originAddress,
            @NotBlank String destinationCity,
            @NotBlank @Size(max = 200) String destinationAddress,
            @NotNull CargoType cargoType,
            @NotBlank @Size(max = 300) String cargoDescription,
            @NotNull @DecimalMin(value = "1", message = "El peso debe ser mayor a 0") @DecimalMax("40000") BigDecimal weightKg,
            @DecimalMin("0.01") BigDecimal volumeM3,
            @NotNull @FutureOrPresent(message = "La fecha de recojo no puede ser pasada") LocalDate pickupDate,
            /** Referencia propia de la PYME (n.º de OC o pedido). Privada: no la ven los transportistas antes de adjudicar. */
            @Size(max = 40) String customerReference,
            /** Valor declarado de la carga en soles (opcional; el transportista responde según su póliza). */
            @DecimalMin(value = "0.01", message = "El valor declarado debe ser mayor a 0") @Digits(integer = 12, fraction = 2) BigDecimal declaredValue,
            /** Contacto, ventana (HH:mm) e instrucciones del recojo: privados hasta adjudicar (RN07, decisión 2026-11-17). */
            @Size(max = 120) String pickupContactName,
            @Size(max = 20) String pickupContactPhone,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String pickupWindowFrom,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String pickupWindowTo,
            @Size(max = 300) String pickupInstructions,
            @Size(max = 120) String deliveryContactName,
            @Size(max = 20) String deliveryContactPhone,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String deliveryWindowFrom,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String deliveryWindowTo,
            @Size(max = 300) String deliveryInstructions,
            /** Direcciones guardadas de las que se publica: solo sirven para ordenar la libreta por uso. */
            UUID originPlaceId, UUID destinationPlaceId) {

        PublishRequestCommand toCommand(String idempotencyKey) {
            var details = new ShipmentRequest.Details(customerReference, declaredValue,
                    new ShipmentRequest.StopDetails(pickupContactName, pickupContactPhone,
                            TimeOfDay.parse(pickupWindowFrom, "pickupWindowFrom"),
                            TimeOfDay.parse(pickupWindowTo, "pickupWindowTo"), pickupInstructions),
                    new ShipmentRequest.StopDetails(deliveryContactName, deliveryContactPhone,
                            TimeOfDay.parse(deliveryWindowFrom, "deliveryWindowFrom"),
                            TimeOfDay.parse(deliveryWindowTo, "deliveryWindowTo"), deliveryInstructions));
            return new PublishRequestCommand(originCity, originAddress, destinationCity, destinationAddress,
                    cargoType, cargoDescription, weightKg, volumeM3, pickupDate, details, originPlaceId,
                    destinationPlaceId, idempotencyKey);
        }
    }

    /** Aviso de posible duplicado: una solicitud publicada hoy con la misma ruta, tipo, peso y fecha de recojo. */
    public record SimilarRequest(UUID id, String code, OffsetDateTime createdAt) {}

    /** Totales de "Mis solicitudes" por estado (sin filtros): para las pestañas de filtro rápido. */
    public record RequestCountsView(long all, long published, long matched, long cancelled, long withBids) {}

    /** Cancelar una solicitud publicada: motivo opcional (lo ven los transportistas que ofertaron; RN07). */
    public record CancelRequestBody(@Size(max = 500) String reason) {}

    /** Reabrir la subasta (F07) o republicar la solicitud cancelada: nueva fecha de recojo opcional. */
    public record RescheduleRequest(@FutureOrPresent(message = "La fecha de recojo no puede ser pasada") LocalDate pickupDate) {}

    public record AuctionView(UUID id, Auction.Status status, OffsetDateTime closesAt, int maxBids,
                              long bidCount, Auction.CloseReason closeReason,
                              /** Cuándo cerró la subasta (null mientras está abierta). */
                              OffsetDateTime closedAt) {}

    /**
     * Posición de la oferta propia del transportista (solo en sus vistas, cuando tiene una oferta no retirada).
     * Las ofertas son selladas (RN04): nunca trae montos ni porcentajes exactos de otras ofertas.
     */
    public record MyBidStanding(
            /** 1 = la mejor (menor monto; empate: la más antigua) entre las ofertas no retiradas. */
            int rank,
            /** Ofertas no retiradas de la subasta, incluida la propia. */
            int bidCount,
            boolean isBest,
            /** Banda frente a la mejor oferta; null si la solicitud ya se adjudicó o canceló. */
            BidStandingBand vsBest,
            /** true si la subasta cerró o la solicitud ya no está publicada: es la posición final. */
            boolean finalStanding) {
        static MyBidStanding of(BidStanding s) {
            return s == null ? null : new MyBidStanding(s.rank(), s.bidCount(), s.best(), s.vsBest(), s.finalStanding());
        }
    }

    public record RequestView(UUID id, UUID requesterOrgId, String requesterName,
                              City originCity, String originAddress,
                              City destinationCity, String destinationAddress,
                              CargoType cargoType, String cargoDescription,
                              BigDecimal weightKg, BigDecimal volumeM3, LocalDate pickupDate,
                              BigDecimal distanceKm, BigDecimal suggestedPrice, BigDecimal priceFloor,
                              ShipmentRequest.Status status, OffsetDateTime createdAt,
                              AuctionView auction,
                              /** Solo para el transportista: su propia oferta, si existe. */
                              BidView myBid,
                              /** true si la organización solicitante tiene el RUC verificado. */
                              boolean requesterRucVerified,
                              /** Vehículo mínimo sugerido según peso y tipo de carga (VehicleRequirementPolicy). */
                              Vehicle.Type minVehicleType,
                              /** Trust Score 0–100 de la PYME solicitante (RN09; 75 sin calificaciones). */
                              BigDecimal requesterTrustScore,
                              /** RN02: tipos de vehículo que pueden llevar esta carga (matriz VehicleCargoCompatibility). */
                              List<Vehicle.Type> compatibleVehicleTypes,
                              /** Motivo de cancelación (de la PYME o del envío adjudicado que se canceló). */
                              String cancelReason,
                              /** Solicitud cancelada de la que se republicó esta (F07). */
                              UUID republishedFrom,
                              /** Solo para la PYME dueña: monto de la mejor oferta PENDIENTE (null sin ofertas pendientes). */
                              BigDecimal bestBidAmount,
                              /** Solo para la PYME dueña: transportista de la mejor oferta pendiente. */
                              String bestBidCarrierName,
                              /** Solo para la PYME dueña: Trust Score del transportista de la mejor oferta. */
                              BigDecimal bestBidCarrierTrustScore,
                              /** Solo para el transportista con oferta: su posición sin montos ajenos. */
                              MyBidStanding myBidStanding,
                              /** Código legible y único (OC-7K2QX); también lo hereda el envío. */
                              String code,
                              /** Valor declarado de la carga en soles (opcional); lo ven también los transportistas. */
                              BigDecimal declaredValue,
                              /** Privado: solo la PYME dueña, el operador y el transportista adjudicado (tras adjudicar). */
                              String customerReference,
                              /** Privado, ídem: contacto, ventana (HH:mm) e instrucciones del recojo. Nulos si no se ven. */
                              String pickupContactName, String pickupContactPhone, String pickupWindowFrom,
                              String pickupWindowTo, String pickupInstructions,
                              /** Privado, ídem: contacto, ventana (HH:mm) e instrucciones de la entrega. */
                              String deliveryContactName, String deliveryContactPhone, String deliveryWindowFrom,
                              String deliveryWindowTo, String deliveryInstructions) {

        static RequestView of(RequestSummary s) {
            ShipmentRequest r = s.request();
            var c = r.cargo();
            var p = r.pricing();
            Auction a = s.auction();
            BidSummary best = s.bestBid();
            var d = r.details();
            boolean priv = s.showPrivate();
            var pk = priv ? d.pickup() : ShipmentRequest.StopDetails.NONE;
            var dl = priv ? d.delivery() : ShipmentRequest.StopDetails.NONE;
            AuctionView av = a == null ? null
                    : new AuctionView(a.id(), a.status(), a.closesAt(), a.maxBids(), s.bidCount(), a.closeReason(),
                            a.closedAt());
            return new RequestView(r.id(), r.requesterOrgId(), s.requesterName(),
                    c.originCity(), c.originAddress(), c.destinationCity(), c.destinationAddress(),
                    c.cargoType(), c.cargoDescription(), c.weightKg(), c.volumeM3(), c.pickupDate(),
                    p.distanceKm(), p.suggestedPrice(), p.priceFloor(), r.status(), r.createdAt(), av,
                    s.myBid() == null ? null : BidView.of(s.myBid()), s.requesterRucVerified(),
                    VehicleRequirementPolicy.minimumFor(c.weightKg(), c.cargoType()), s.requesterTrustScore(),
                    VehicleCargoCompatibility.allowedFor(c.cargoType()), r.cancelReason(), r.republishedFrom(),
                    best == null ? null : best.bid().amount(), best == null ? null : best.carrierName(),
                    best == null ? null : best.carrierTrustScore(), MyBidStanding.of(s.myStanding()),
                    r.code(), d.declaredValue(), priv ? d.customerReference() : null,
                    pk.contactName(), pk.contactPhone(), TimeOfDay.format(pk.windowFrom()), TimeOfDay.format(pk.windowTo()),
                    pk.instructions(), dl.contactName(), dl.contactPhone(), TimeOfDay.format(dl.windowFrom()),
                    TimeOfDay.format(dl.windowTo()), dl.instructions());
        }

        static List<RequestView> of(List<RequestSummary> list) {
            return list.stream().map(RequestView::of).toList();
        }
    }

    public record PlaceBid(@NotNull UUID vehicleId,
                           @NotNull @DecimalMin(value = "1") BigDecimal amount,
                           @Size(max = 300) String notes) {

        PlaceBidCommand toCommand() {
            return new PlaceBidCommand(vehicleId, amount, notes);
        }
    }

    public record BidView(UUID id, UUID requestId, UUID carrierOrgId, String carrierName,
                          boolean carrierRucVerified, UUID vehicleId, BigDecimal amount, String notes,
                          Bid.Status status, OffsetDateTime createdAt,
                          /** Tipo del vehículo ofertado (copiado al ofertar). */
                          Vehicle.Type vehicleType,
                          /** Placa del vehículo ofertado (copiada al ofertar). */
                          String vehiclePlate,
                          /** Trust Score 0–100 del transportista (RN09; 75 sin calificaciones). */
                          BigDecimal carrierTrustScore,
                          /** true si la registró el operador al asignar manualmente (RN10). */
                          boolean assignedByOperator,
                          /** Solo al ofertar (POST): avisos de agenda del vehículo, p. ej. LOGYX-CAP-006. Nulo en listados. */
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<BidWarningView> warnings) {

        static BidView of(BidSummary s) {
            return of(s, null);
        }

        static BidView of(BidSummary s, List<BidWarningView> warnings) {
            Bid b = s.bid();
            return new BidView(b.id(), b.requestId(), b.carrierOrgId(), s.carrierName(), s.carrierRucVerified(),
                    b.vehicleId(), b.amount(), b.notes(), b.status(), b.createdAt(),
                    b.vehicleType(), b.vehiclePlate(), s.carrierTrustScore(), b.assignedByOperator(), warnings);
        }
    }

    /**
     * Elegibilidad de un vehículo del transportista para ofertar a una solicitud (RN02 por fecha de recojo).
     * {@code eligible=false}: {@code blockerCode}/{@code blockerMessage} dicen por qué (CAP-001..005, CAP-007) y
     * {@code freeFrom} desde cuándo estaría libre por envíos activos. {@code busy}: lo que ocupa la ventana
     * ({@code SHIPMENT}: envío activo, bloquea; {@code BID}: oferta pendiente, solo avisa). {@code warnings}: CAP-006.
     */
    public record VehicleEligibilityView(UUID vehicleId, String plate, Vehicle.Type type, BigDecimal maxWeightKg,
                                         BigDecimal maxVolumeM3, Vehicle.Status status, boolean eligible,
                                         String blockerCode, String blockerMessage, LocalDate freeFrom,
                                         List<BusySlot> busy, List<BidWarningView> warnings) {
        static VehicleEligibilityView of(pe.logyx.marketplace.application.VehicleAgenda.Assessment a) {
            var v = a.vehicle();
            var all = new java.util.ArrayList<BusySlot>();
            a.shipmentConflicts().forEach(s -> all.add(BusySlot.of(s)));
            a.bidOverlaps().forEach(s -> all.add(BusySlot.of(s)));
            return new VehicleEligibilityView(v.id(), v.plate(), v.type(), v.maxWeightKg(), v.maxVolumeM3(),
                    v.status(), a.eligible(), a.blocker() == null ? null : a.blocker().code(),
                    a.blocker() == null ? null : a.blocker().getMessage(), a.freeFrom(), all,
                    a.warnings().stream().map(w -> new BidWarningView(w.code(), w.message())).toList());
        }
    }

    public record BusySlot(String kind, String label, LocalDate from, LocalDate to) {
        static BusySlot of(pe.logyx.marketplace.application.VehicleAgenda.Slot s) {
            return new BusySlot(s.kind().name(), s.label(), s.from(), s.to());
        }
    }

    /** Elegibilidad de toda la flota para una solicitud y la ventana de ocupación de la carga. */
    public record EligibilityView(LocalDate windowFrom, LocalDate windowTo, int maxOverlappingBids,
                                  List<VehicleEligibilityView> vehicles) {}

    /** Aviso de agenda al ofertar (no bloquea). */
    public record BidWarningView(String code, String message) {}

    public record AcceptResult(UUID shipmentId, BidView acceptedBid, List<UUID> rejectedBidIds) {
        static AcceptResult of(AcceptOutcome o) {
            return new AcceptResult(o.shipmentId(), BidView.of(o.acceptedBid()), o.rejectedBidIds());
        }
    }
}
