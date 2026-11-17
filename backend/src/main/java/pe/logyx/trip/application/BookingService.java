package pe.logyx.trip.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.DirectContractCommand;
import pe.logyx.marketplace.application.DirectContractUseCase;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.CancelShipmentUseCase;
import pe.logyx.trip.application.event.TripBookingCancelled;
import pe.logyx.trip.application.event.TripBookingConfirmed;
import pe.logyx.trip.application.event.TripBookingRejected;
import pe.logyx.trip.application.event.TripBookingRequested;
import pe.logyx.trip.application.port.out.CarrierTripRepository;
import pe.logyx.trip.application.port.out.TripBookingRepository;
import pe.logyx.trip.domain.TripCapacityPolicy;
import pe.logyx.trip.domain.VehicleCargoRules;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripBooking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Reservas de espacio: la PYME reserva (RF22/RF23), el transportista confirma o rechaza y la PYME puede cancelar.
 * Confirmar descuenta capacidad (RN12) y genera el envío reutilizando shipment (ver {@code DirectContractUseCase}).
 */
@ApplicationScoped
public class BookingService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject CarrierTripRepository trips;
    @Inject TripBookingRepository bookings;
    @Inject OrganizationDirectory organizations;
    @Inject pe.logyx.identity.application.OrganizationGuard guard;
    @Inject DirectContractUseCase contracts;
    @Inject CancelShipmentUseCase shipments;
    @Inject BookingSweeper sweeper;
    @Inject TripSummaries summaries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject SystemTx system;
    @Inject Event<TripBookingRequested> requested;
    @Inject Event<TripBookingConfirmed> confirmed;
    @Inject Event<TripBookingRejected> rejected;
    @Inject Event<TripBookingCancelled> cancelled;

    // ----- PYME

    @Transactional
    public BookingSummary book(UUID tripId, BookTripCommand cmd) {
        Organization org = guard.ensureCanTransact(tenant.orgId());                            // RN01 (misma exigencia)
        CarrierTrip trip = trips.findById(tripId).orElseThrow(() -> BusinessException.notFound("Viaje"));
        OffsetDateTime now = OffsetDateTime.now();
        trip.ensureAcceptsBookings(now);
        TripCapacityPolicy.ensureFits(cmd.weightKg(), trip.remainingKg());                     // RF23 / RN12
        VehicleCargoRules.ensureCanCarry(trip.vehicleType(), cmd.cargoType());
        TripBooking b = TripBooking.request(trip, org.id(), tenant.profileId(), cmd.weightKg(), cmd.cargoType(),
                cmd.cargoDescription(), cmd.pickupAddress(), cmd.deliveryAddress(), trip.priceFor(cmd.weightKg()), now);
        bookings.save(b);
        audit.record(tenant.profileId(), org.id(), "TRIP_BOOKING_REQUESTED", "trip_booking", b.id(),
                "kg=" + b.weightKg() + " precio=" + b.price());
        requested.fire(new TripBookingRequested(b.id(), trip.id(), trip.carrierOrgId(), org.id(), org.legalName(),
                trip.routeLabel(), b.weightKg(), b.price()));
        return summaries.bookings(java.util.List.of(b)).getFirst();
    }

    /**
     * La PYME cancela su reserva. Pendiente: se anula. Confirmada: se cancela el envío si aún no salió
     * (409 LOGYX-SHP-003 si ya salió) y se libera la capacidad del viaje.
     */
    @Transactional
    public BookingSummary cancelByRequester(UUID bookingId) {
        TripBooking b = bookings.findById(bookingId).filter(x -> x.requesterOrgId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Reserva"));
        boolean wasConfirmed = b.isConfirmed();
        OffsetDateTime now = OffsetDateTime.now();
        b.cancel("Cancelada por la PYME", now);
        if (wasConfirmed) {
            if (b.shipmentId() != null) shipments.cancelPending(b.shipmentId());
            UUID tripId = b.tripId();
            BigDecimal weight = b.weightKg();
            // SystemTx: devolver la capacidad modifica carrier_trips, que es del transportista (trips_owner); la PYME
            // solo puede leerlo. Se bloquea la fila del viaje y se resta el peso de esa reserva, nada más.
            system.run(() -> trips.findByIdForUpdate(tripId).ifPresent(t -> {
                t.releaseBooking(weight);
                trips.save(t);
            }));
        }
        bookings.save(b);
        audit.record(tenant.profileId(), tenant.orgId(), "TRIP_BOOKING_CANCELLED", "trip_booking", b.id(), null);
        cancelled.fire(new TripBookingCancelled(b.id(), b.tripId(), b.carrierOrgId(), b.requesterOrgId(),
                organizations.get(b.requesterOrgId()).legalName(), b.routeLabel(), wasConfirmed));
        return summaries.bookings(java.util.List.of(b)).getFirst();
    }

    // ----- Transportista

    /** Confirma: valida la capacidad restante (RN12), crea solicitud adjudicada + envío y descuenta el peso. */
    @Transactional
    public BookingSummary confirm(UUID bookingId) {
        TripBooking b = ownBooking(bookingId);
        b.ensurePending();
        CarrierTrip trip = trips.findByIdForUpdate(b.tripId()).orElseThrow(() -> BusinessException.notFound("Viaje"));
        OffsetDateTime now = OffsetDateTime.now();
        trip.ensureAcceptsBookings(now);
        TripCapacityPolicy.ensureFits(b.weightKg(), trip.remainingKg());                       // RN12

        // El contrato (solicitud + oferta + envío) se confirma en su propia transacción; es lo último que puede fallar
        // por reglas de negocio, así que va después de todas las validaciones.
        var contract = contracts.create(new DirectContractCommand(b.requesterOrgId(), b.requestedBy(),
                b.carrierOrgId(), trip.vehicleId(), trip.vehicleType(), trip.vehiclePlate(), b.originCity(),
                b.pickupAddress(), b.destinationCity(), b.deliveryAddress(), b.cargoType(), b.cargoDescription(),
                b.weightKg(), trip.departureAt().atZoneSameInstant(LIMA).toLocalDate(), b.price(), trip.id()));
        trip.confirmBooking(b.weightKg());
        b.confirm(contract.requestId(), contract.shipmentId(), now);
        trips.save(trip);
        bookings.save(b);
        if (!pe.logyx.trip.domain.TripStatusPolicy.acceptsBookings(trip.status())) {
            sweeper.rejectPending(trip, TripService.CLOSE_NOTE);                                // viaje lleno
        }
        audit.record(tenant.profileId(), tenant.orgId(), "TRIP_BOOKING_CONFIRMED", "trip_booking", b.id(),
                "envio=" + contract.shipmentId());
        confirmed.fire(new TripBookingConfirmed(b.id(), trip.id(), contract.shipmentId(), b.carrierOrgId(),
                b.requesterOrgId(), organizations.get(b.carrierOrgId()).legalName(), b.routeLabel(), b.price()));
        return summaries.bookings(java.util.List.of(b)).getFirst();
    }

    @Transactional
    public BookingSummary reject(UUID bookingId, String reason) {
        TripBooking b = ownBooking(bookingId);
        b.reject(reason, OffsetDateTime.now());
        bookings.save(b);
        audit.record(tenant.profileId(), tenant.orgId(), "TRIP_BOOKING_REJECTED", "trip_booking", b.id(), null);
        rejected.fire(new TripBookingRejected(b.id(), b.tripId(), b.carrierOrgId(), b.requesterOrgId(),
                organizations.get(b.carrierOrgId()).legalName(), b.routeLabel(), b.decisionNote()));
        return summaries.bookings(java.util.List.of(b)).getFirst();
    }

    private TripBooking ownBooking(UUID bookingId) {
        return bookings.findById(bookingId).filter(x -> x.carrierOrgId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Reserva"));
    }
}
