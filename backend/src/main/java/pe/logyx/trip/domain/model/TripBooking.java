package pe.logyx.trip.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Reserva de espacio de una PYME en un viaje publicado. */
public final class TripBooking {

    private final UUID id;
    private final UUID tripId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final UUID requestedBy;
    private final City originCity;
    private final City destinationCity;
    private final OffsetDateTime departureAt;
    private final BigDecimal weightKg;
    private final CargoType cargoType;
    private final String cargoDescription;
    private final String pickupAddress;
    private final String deliveryAddress;
    private final BigDecimal price;
    private BookingStatus status;
    private String decisionNote;
    private UUID requestId;
    private UUID shipmentId;
    private final OffsetDateTime createdAt;
    private OffsetDateTime decidedAt;

    public TripBooking(UUID id, UUID tripId, UUID requesterOrgId, UUID carrierOrgId, UUID requestedBy, City originCity,
                       City destinationCity, OffsetDateTime departureAt, BigDecimal weightKg, CargoType cargoType,
                       String cargoDescription, String pickupAddress, String deliveryAddress, BigDecimal price,
                       BookingStatus status, String decisionNote, UUID requestId, UUID shipmentId,
                       OffsetDateTime createdAt, OffsetDateTime decidedAt) {
        this.id = id;
        this.tripId = tripId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.requestedBy = requestedBy;
        this.originCity = originCity;
        this.destinationCity = destinationCity;
        this.departureAt = departureAt;
        this.weightKg = weightKg;
        this.cargoType = cargoType;
        this.cargoDescription = cargoDescription;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.price = price;
        this.status = status;
        this.decisionNote = decisionNote;
        this.requestId = requestId;
        this.shipmentId = shipmentId;
        this.createdAt = createdAt;
        this.decidedAt = decidedAt;
    }

    public static TripBooking request(CarrierTrip trip, UUID requesterOrgId, UUID requestedBy, BigDecimal weightKg,
                                      CargoType cargoType, String cargoDescription, String pickupAddress,
                                      String deliveryAddress, BigDecimal price, OffsetDateTime now) {
        return new TripBooking(UUID.randomUUID(), trip.id(), requesterOrgId, trip.carrierOrgId(), requestedBy,
                trip.originCity(), trip.destinationCity(), trip.departureAt(), weightKg, cargoType,
                ContactInfoPolicy.clean(cargoDescription, "cargoDescription"),                        // RN07
                ContactInfoPolicy.clean(pickupAddress, "pickupAddress"),
                ContactInfoPolicy.clean(deliveryAddress, "deliveryAddress"), price, BookingStatus.PENDING,
                null, null, null, now, null);
    }

    public boolean isPending() { return status == BookingStatus.PENDING; }
    public boolean isConfirmed() { return status == BookingStatus.CONFIRMED; }
    public boolean isActive() { return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED; }

    /** "Lima → Juliaca", para textos de notificaciones. */
    public String routeLabel() { return originCity.label + " → " + destinationCity.label; }

    public void ensurePending() {
        if (!isPending()) {
            throw BusinessException.conflict("LOGYX-TRP-006", "La reserva ya fue resuelta (" + status + ")", "status");
        }
    }

    public void confirm(UUID requestId, UUID shipmentId, OffsetDateTime now) {
        ensurePending();
        this.status = BookingStatus.CONFIRMED;
        this.requestId = requestId;
        this.shipmentId = shipmentId;
        this.decidedAt = now;
    }

    public void reject(String note, OffsetDateTime now) {
        ensurePending();
        String clean = ContactInfoPolicy.clean(note, "reason");                       // RN07: lo lee la PYME
        this.status = BookingStatus.REJECTED;
        this.decisionNote = clean;
        this.decidedAt = now;
    }

    /** La PYME cancela, o el viaje se cancela. Solo PENDING o CONFIRMED. */
    public void cancel(String note, OffsetDateTime now) {
        if (!isActive()) {
            throw BusinessException.conflict("LOGYX-TRP-006", "La reserva ya fue resuelta (" + status + ")", "status");
        }
        this.status = BookingStatus.CANCELLED;
        this.decisionNote = note;
        this.decidedAt = now;
    }

    public UUID id() { return id; }
    public UUID tripId() { return tripId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public UUID requestedBy() { return requestedBy; }
    public City originCity() { return originCity; }
    public City destinationCity() { return destinationCity; }
    public OffsetDateTime departureAt() { return departureAt; }
    public BigDecimal weightKg() { return weightKg; }
    public CargoType cargoType() { return cargoType; }
    public String cargoDescription() { return cargoDescription; }
    public String pickupAddress() { return pickupAddress; }
    public String deliveryAddress() { return deliveryAddress; }
    public BigDecimal price() { return price; }
    public BookingStatus status() { return status; }
    public String decisionNote() { return decisionNote; }
    public UUID requestId() { return requestId; }
    public UUID shipmentId() { return shipmentId; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime decidedAt() { return decidedAt; }
}
