package pe.logyx.trip.domain.model;

import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.trip.domain.TripCapacityPolicy;
import pe.logyx.trip.domain.TripPricingPolicy;
import pe.logyx.trip.domain.TripStatusPolicy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Viaje publicado por un transportista con capacidad disponible (supply-side, OE de viajes disponibles). */
public final class CarrierTrip {

    private final UUID id;
    private final UUID carrierOrgId;
    private final UUID vehicleId;
    /** Copias al publicar: la flota es privada del transportista (RLS). */
    private final Vehicle.Type vehicleType;
    private final String vehiclePlate;
    private final City originCity;
    private final City destinationCity;
    private final OffsetDateTime departureAt;
    private final BigDecimal capacityKg;
    private BigDecimal confirmedWeightKg;
    private final TripPricingMode pricingMode;
    private final BigDecimal rate;
    private final String notes;
    private TripStatus status;
    private final OffsetDateTime createdAt;

    public CarrierTrip(UUID id, UUID carrierOrgId, UUID vehicleId, Vehicle.Type vehicleType, String vehiclePlate,
                       City originCity, City destinationCity, OffsetDateTime departureAt, BigDecimal capacityKg,
                       BigDecimal confirmedWeightKg, TripPricingMode pricingMode, BigDecimal rate, String notes,
                       TripStatus status, OffsetDateTime createdAt) {
        this.id = id;
        this.carrierOrgId = carrierOrgId;
        this.vehicleId = vehicleId;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.originCity = originCity;
        this.destinationCity = destinationCity;
        this.departureAt = departureAt;
        this.capacityKg = capacityKg;
        this.confirmedWeightKg = confirmedWeightKg;
        this.pricingMode = pricingMode;
        this.rate = rate;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Publica un viaje: RN12 (capacidad ≤ vehículo) y salida futura. */
    public static CarrierTrip publish(UUID carrierOrgId, Vehicle vehicle, City origin, City destination,
                                      OffsetDateTime departureAt, BigDecimal capacityKg, TripPricingMode mode,
                                      BigDecimal rate, String notes, OffsetDateTime now) {
        if (origin == destination) {
            throw BusinessException.badRequest("LOGYX-PRC-002", "Origen y destino deben ser distintos");
        }
        if (!departureAt.isAfter(now)) {
            throw BusinessException.badRequest("LOGYX-TRP-002", "La fecha de salida debe ser futura", "departureAt");
        }
        TripCapacityPolicy.ensurePublishable(capacityKg, vehicle.maxWeightKg());                       // RN12
        return new CarrierTrip(UUID.randomUUID(), carrierOrgId, vehicle.id(), vehicle.type(), vehicle.plate(), origin,
                destination, departureAt, capacityKg, BigDecimal.ZERO, mode, rate,
                ContactInfoPolicy.clean(notes, "notes"), TripStatus.OPEN, now);                       // RN07
    }

    public boolean isOwnedBy(UUID orgId) { return carrierOrgId.equals(orgId); }

    public BigDecimal remainingKg() { return TripCapacityPolicy.remaining(capacityKg, confirmedWeightKg); }

    public BigDecimal priceFor(BigDecimal weightKg) {
        return TripPricingPolicy.price(pricingMode, rate, capacityKg, weightKg);
    }

    /** "Lima → Juliaca", para textos de notificaciones. */
    public String routeLabel() { return originCity.label + " → " + destinationCity.label; }

    /** Reservar exige estado OPEN/FILLING y salida futura. */
    public void ensureAcceptsBookings(OffsetDateTime now) {
        if (!TripStatusPolicy.acceptsBookings(status) || !departureAt.isAfter(now)) {
            throw BusinessException.conflict("LOGYX-TRP-003", "El viaje ya no admite reservas");
        }
    }

    /** Se confirmó una reserva de {@code weightKg}: descuenta capacidad (RN12) y avanza el estado. */
    public void confirmBooking(BigDecimal weightKg) {
        if (!TripStatusPolicy.acceptsBookings(status)) {
            throw BusinessException.conflict("LOGYX-TRP-003", "El viaje ya no admite reservas");
        }
        TripCapacityPolicy.ensureFits(weightKg, remainingKg());
        confirmedWeightKg = confirmedWeightKg.add(weightKg);
        status = TripStatusPolicy.afterConfirmation(status, remainingKg());
    }

    /** Se canceló una reserva confirmada: recupera la capacidad. */
    public void releaseBooking(BigDecimal weightKg) {
        confirmedWeightKg = confirmedWeightKg.subtract(weightKg).max(BigDecimal.ZERO);
        status = TripStatusPolicy.afterRelease(status, confirmedWeightKg);
    }

    public void close() { move(TripStatus.CLOSED); }

    public void cancel() { move(TripStatus.CANCELLED); }

    public void complete() { move(TripStatus.COMPLETED); }

    private void move(TripStatus to) {
        TripStatusPolicy.ensureTransition(status, to);
        status = to;
    }

    public UUID id() { return id; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public UUID vehicleId() { return vehicleId; }
    public Vehicle.Type vehicleType() { return vehicleType; }
    public String vehiclePlate() { return vehiclePlate; }
    public City originCity() { return originCity; }
    public City destinationCity() { return destinationCity; }
    public OffsetDateTime departureAt() { return departureAt; }
    public BigDecimal capacityKg() { return capacityKg; }
    public BigDecimal confirmedWeightKg() { return confirmedWeightKg; }
    public TripPricingMode pricingMode() { return pricingMode; }
    public BigDecimal rate() { return rate; }
    public String notes() { return notes; }
    public TripStatus status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
}
