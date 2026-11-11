package pe.logyx.trip.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.trip.application.*;
import pe.logyx.trip.domain.SmartLoadPlanner;
import pe.logyx.trip.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TripDtos {
    private TripDtos() {}

    // ----------------------------------------------------------------- Viajes

    public record CreateTripRequest(
            @NotBlank String originCity,
            @NotBlank String destinationCity,
            @NotNull @Future(message = "La fecha de salida debe ser futura") OffsetDateTime departureAt,
            @NotNull UUID vehicleId,
            @NotNull @DecimalMin(value = "1", message = "La capacidad debe ser mayor a 0") @DecimalMax("40000")
            BigDecimal capacityKg,
            @NotNull TripPricingMode pricingMode,
            @NotNull @DecimalMin(value = "0.01", message = "La tarifa debe ser mayor a 0") @DecimalMax("1000000")
            BigDecimal rate,
            @Size(max = 300) String notes) {

        PublishTripCommand toCommand() {
            return new PublishTripCommand(originCity, destinationCity, departureAt, vehicleId, capacityKg,
                    pricingMode, rate, notes);
        }
    }

    public record TripView(UUID id, UUID carrierOrgId, String carrierName,
                           /** Trust Score 0–100 del transportista (RN09). */
                           BigDecimal carrierTrustScore,
                           /** Solo para el transportista dueño; null para las PYMEs (la flota es privada). */
                           UUID vehicleId,
                           Vehicle.Type vehicleType, String vehiclePlate,
                           City originCity, City destinationCity, OffsetDateTime departureAt,
                           /** Capacidad publicada (RN12: ≤ capacidad del vehículo). */
                           BigDecimal capacityKg,
                           /** Peso de las reservas confirmadas. */
                           BigDecimal confirmedWeightKg,
                           /** capacityKg − confirmedWeightKg. */
                           BigDecimal remainingKg,
                           TripPricingMode pricingMode,
                           /** PER_KG: soles por kg. PER_TRIP: tarifa de todo el tramo (se prorratea por peso). */
                           BigDecimal rate,
                           String notes, TripStatus status, OffsetDateTime createdAt,
                           /** Solo transportista dueño: reservas por confirmar. */
                           Long pendingBookings,
                           /** Solo transportista dueño: reservas confirmadas. */
                           Long confirmedBookings) {

        static TripView of(TripSummary s) {
            CarrierTrip t = s.trip();
            boolean owner = s.pendingBookings() != null;
            return new TripView(t.id(), t.carrierOrgId(), s.carrierName(), s.carrierTrustScore(),
                    owner ? t.vehicleId() : null, t.vehicleType(), t.vehiclePlate(), t.originCity(),
                    t.destinationCity(), t.departureAt(), t.capacityKg(), t.confirmedWeightKg(), t.remainingKg(),
                    t.pricingMode(), t.rate(), t.notes(), t.status(), t.createdAt(), s.pendingBookings(),
                    s.confirmedBookings());
        }
    }

    // --------------------------------------------------------------- Reservas

    public record CreateTripBookingRequest(
            @NotNull @DecimalMin(value = "1", message = "El peso debe ser mayor a 0") @DecimalMax("40000")
            BigDecimal weightKg,
            @NotBlank @Size(max = 300) String cargoDescription,
            @NotNull CargoType cargoType,
            @NotBlank @Size(max = 200) String pickupAddress,
            @NotBlank @Size(max = 200) String deliveryAddress) {

        BookTripCommand toCommand() {
            return new BookTripCommand(weightKg, cargoDescription, cargoType, pickupAddress, deliveryAddress);
        }
    }

    public record RejectTripBookingRequest(@Size(max = 300) String reason) {}

    public record TripBookingView(UUID id, UUID tripId, BookingStatus status,
                                  UUID requesterOrgId, String requesterName, UUID carrierOrgId, String carrierName,
                                  City originCity, City destinationCity, OffsetDateTime departureAt,
                                  BigDecimal weightKg, CargoType cargoType, String cargoDescription,
                                  String pickupAddress, String deliveryAddress,
                                  /** Precio calculado al reservar (ver TripPricingPolicy). */
                                  BigDecimal price,
                                  /** Motivo del rechazo o de la cancelación, si lo hay. */
                                  String decisionNote,
                                  /** Solicitud adjudicada generada al confirmar (null mientras esté PENDING). */
                                  UUID requestId,
                                  /** Envío generado al confirmar (null mientras esté PENDING). */
                                  UUID shipmentId,
                                  OffsetDateTime createdAt, OffsetDateTime decidedAt) {

        static TripBookingView of(BookingSummary s) {
            TripBooking b = s.booking();
            return new TripBookingView(b.id(), b.tripId(), b.status(), b.requesterOrgId(), s.requesterName(),
                    b.carrierOrgId(), s.carrierName(), b.originCity(), b.destinationCity(), b.departureAt(),
                    b.weightKg(), b.cargoType(), b.cargoDescription(), b.pickupAddress(), b.deliveryAddress(),
                    b.price(), b.decisionNote(), b.requestId(), b.shipmentId(), b.createdAt(), b.decidedAt());
        }
    }

    // ---------------------------------------------------------- Carga de retorno

    public record ReturnLoadView(UUID requestId, UUID requesterOrgId, String requesterName,
                                 /** Envío del transportista que dejará el vehículo cerca del origen. */
                                 UUID shipmentId, UUID vehicleId, String vehiclePlate,
                                 /** Ciudad donde termina ese envío. */
                                 City fromCity, boolean shipmentDelivered,
                                 City originCity, String originAddress, City destinationCity, String destinationAddress,
                                 CargoType cargoType, String cargoDescription, BigDecimal weightKg,
                                 LocalDate pickupDate, BigDecimal suggestedPrice, BigDecimal priceFloor,
                                 /** Distancia por carretera entre fromCity y originCity (0 = misma ciudad). */
                                 double distanceKm,
                                 /** Días entre que el vehículo queda libre y el recojo. */
                                 int waitDays,
                                 /** Compatibilidad 0–100 (cercanía y espera). */
                                 int matchScore,
                                 /** true si ya ofertó en esta solicitud. */
                                 boolean alreadyBid) {

        static ReturnLoadView of(ReturnLoadMatch m) {
            var r = m.request().request();
            var c = r.cargo();
            var leg = m.match().leg();
            return new ReturnLoadView(r.id(), r.requesterOrgId(), m.request().requesterName(), leg.shipmentId(),
                    leg.vehicleId(), leg.vehiclePlate(), leg.destination(), leg.delivered(), c.originCity(),
                    c.originAddress(), c.destinationCity(), c.destinationAddress(), c.cargoType(),
                    c.cargoDescription(), c.weightKg(), c.pickupDate(), r.pricing().suggestedPrice(),
                    r.pricing().priceFloor(), m.match().distanceKm(), m.match().waitDays(), m.match().score(),
                    m.alreadyBid());
        }
    }

    // -------------------------------------------------------------- Smart Load

    public record SmartLoadItemView(UUID requestId, City originCity, City destinationCity, BigDecimal weightKg,
                                    /** Precio sugerido de la solicitud (aporte al ingreso estimado). */
                                    BigDecimal income, LocalDate pickupDate, int pickupOrder, int deliveryOrder) {}

    public record SmartLoadStopView(SmartLoadPlanner.StopType type, City city, UUID requestId) {}

    public record SmartLoadView(UUID id, UUID vehicleId, Vehicle.Type vehicleType, String vehiclePlate,
                                BigDecimal capacityKg, BigDecimal totalWeightKg,
                                /** Suma de los precios sugeridos de las solicitudes. */
                                BigDecimal combinedIncome,
                                /** totalWeightKg / capacityKg × 100 (≥ 60). */
                                BigDecimal occupancyPct,
                                SmartLoadStatus status, OffsetDateTime createdAt, OffsetDateTime expiresAt,
                                OffsetDateTime decidedAt, List<SmartLoadItemView> items,
                                /** Plan de ruta: recojos por orden y luego entregas. */
                                List<SmartLoadStopView> route) {

        static SmartLoadView of(SmartLoadSuggestion s) {
            return new SmartLoadView(s.id(), s.vehicleId(), s.vehicleType(), s.vehiclePlate(), s.capacityKg(),
                    s.totalWeightKg(), s.combinedIncome(), s.occupancyPct(), s.status(), s.createdAt(), s.expiresAt(),
                    s.decidedAt(),
                    s.items().stream().map(i -> new SmartLoadItemView(i.requestId(), i.originCity(),
                            i.destinationCity(), i.weightKg(), i.income(), i.pickupDate(), i.pickupOrder(),
                            i.deliveryOrder())).toList(),
                    s.routePlan().stream().map(p -> new SmartLoadStopView(p.type(), p.city(), p.requestId())).toList());
        }
    }
}
