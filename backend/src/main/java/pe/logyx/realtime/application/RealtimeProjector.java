package pe.logyx.realtime.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.incident.application.event.IncidentReported;
import pe.logyx.incident.application.event.IncidentStatusChanged;
import pe.logyx.marketplace.application.event.BidAccepted;
import pe.logyx.marketplace.application.event.BidPlaced;
import pe.logyx.negotiation.application.event.CounterOfferUpdated;
import pe.logyx.negotiation.application.event.MessagePosted;
import pe.logyx.notification.application.event.NotificationsPublished;
import pe.logyx.realtime.application.port.out.RealtimeEventRepository;
import pe.logyx.realtime.application.port.out.ShipmentDrivers;
import pe.logyx.realtime.domain.model.Recipient;
import pe.logyx.reputation.application.event.ReviewSubmitted;
import pe.logyx.shared.outbox.Outboxed;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shipment.application.event.DriverAssigned;
import pe.logyx.shipment.application.event.ShipmentStatusChanged;
import pe.logyx.shipment.application.event.ShipmentStopUpdated;
import pe.logyx.trip.application.event.SmartLoadAccepted;
import pe.logyx.trip.application.event.SmartLoadSuggested;
import pe.logyx.trip.application.event.TripBookingCancelled;
import pe.logyx.trip.application.event.TripBookingConfirmed;
import pe.logyx.trip.application.event.TripBookingRejected;
import pe.logyx.trip.application.event.TripBookingRequested;
import pe.logyx.trip.application.event.TripCancelled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static pe.logyx.realtime.domain.RealtimeEventTypes.*;

/**
 * Proyecta los eventos de dominio del outbox en {@code realtime_events} (ADR-0007): una fila por destinatario con un
 * payload mínimo (ids, estados y contadores; nunca montos, nombres, textos de chat ni datos de contacto). El
 * cliente vuelve a pedir por la API (con RLS) lo que necesite mostrar.
 *
 * <p>Corre dentro de la transacción de sistema del despacho del outbox ({@code @Outboxed}): la proyección, las
 * notificaciones y la marca {@code DONE} se confirman juntas; al confirmarse, el trigger de V12 hace NOTIFY y cada
 * instancia entrega a sus conexiones. Si falla, el outbox reintenta todo (no hay duplicados: lo revertido no se ve).
 *
 * <p>Destinatarios: siempre las partes del evento (incluida la que actuó, para sus otras pestañas/dispositivos), el
 * conductor del envío dirigido a su usuario, y difusiones por tipo solo con datos que ese tipo ya puede leer
 * (conteo de ofertas para transportistas, RF15; incidencias para operadores).
 */
@ApplicationScoped
public class RealtimeProjector {

    @Inject RealtimeEventRepository events;
    @Inject ShipmentDrivers drivers;

    // ----- Subasta y ofertas

    void onBidPlaced(@Observes @Outboxed BidPlaced e) {
        Map<String, Object> d = data("requestId", e.requestId(), "bidId", e.bidId(), "bidCount", e.bidCount(),
                "auctionClosed", e.auctionClosed());
        emit(BID_PLACED, d, Recipient.org(e.requesterOrgId()), Recipient.org(e.carrierOrgId()));
        emit(AUCTION_UPDATED, data("requestId", e.requestId(), "bidCount", e.bidCount(), "auctionClosed",
                e.auctionClosed()), Recipient.broadcast(OrgType.CARRIER));
    }

    void onBidAccepted(@Observes @Outboxed BidAccepted e) {
        Map<String, Object> d = data("requestId", e.requestId(), "bidId", e.bidId(), "shipmentId", e.shipmentId(),
                "byOperator", e.byOperator());
        emit(REQUEST_MATCHED, d, Recipient.org(e.requesterOrgId()));
        emit(BID_ACCEPTED, d, Recipient.org(e.carrierOrgId()));
        if (e.rejected() != null) {
            e.rejected().forEach(r -> emit(BID_REJECTED, data("requestId", e.requestId(), "bidId", r.bidId()),
                    Recipient.org(r.carrierOrgId())));
        }
        emit(AUCTION_UPDATED, data("requestId", e.requestId(), "auctionClosed", true),
                Recipient.broadcast(OrgType.CARRIER));
    }

    void onCounterOffer(@Observes @Outboxed CounterOfferUpdated e) {
        emit(COUNTER_OFFER_UPDATED, data("counterOfferId", e.counterOfferId(), "bidId", e.bidId(),
                        "requestId", e.requestId(), "status", e.status()),
                Recipient.org(e.requesterOrgId()), Recipient.org(e.carrierOrgId()));
    }

    // ----- Chat (el texto NO viaja: el cliente recarga el hilo)

    void onMessage(@Observes @Outboxed MessagePosted e) {
        emit(MESSAGE_POSTED, data("requestId", e.requestId(), "carrierOrgId", e.carrierOrgId(),
                        "messageId", e.messageId(), "senderOrgId", e.senderOrgId()),
                orgs(e.senderOrgId(), e.recipientOrgId()));
    }

    // ----- Envíos

    void onShipmentStatus(@Observes @Outboxed ShipmentStatusChanged e) {
        emitToShipment(SHIPMENT_STATUS_CHANGED, data("shipmentId", e.shipmentId(), "status", e.status()),
                e.shipmentId(), e.requesterOrgId(), e.carrierOrgId(), null);
    }

    void onStopUpdated(@Observes @Outboxed ShipmentStopUpdated e) {
        emitToShipment(SHIPMENT_STOP_UPDATED, data("shipmentId", e.shipmentId(), "stopId", e.stopId(),
                        "stopStatus", e.stopStatus(), "status", e.shipmentStatus()),
                e.shipmentId(), e.requesterOrgId(), e.carrierOrgId(), e.driverProfileId());
    }

    void onDriverAssigned(@Observes @Outboxed DriverAssigned e) {
        List<Recipient> to = new ArrayList<>(List.of(Recipient.org(e.requesterOrgId()), Recipient.org(e.carrierOrgId())));
        if (e.driverProfileId() != null) to.add(Recipient.user(e.carrierOrgId(), e.driverProfileId()));
        emit(SHIPMENT_DRIVER_ASSIGNED, data("shipmentId", e.shipmentId(), "assigned", true), to.toArray(Recipient[]::new));
        if (e.previousDriverProfileId() != null && !e.previousDriverProfileId().equals(e.driverProfileId())) {
            emit(SHIPMENT_DRIVER_ASSIGNED, data("shipmentId", e.shipmentId(), "assigned", false),
                    Recipient.user(e.carrierOrgId(), e.previousDriverProfileId()));
        }
    }

    // ----- Incidencias (las partes, el conductor del envío y los operadores)

    void onIncidentReported(@Observes @Outboxed IncidentReported e) {
        Map<String, Object> d = data("incidentId", e.incidentId(), "shipmentId", e.shipmentId(), "status", "OPEN");
        emitToShipment(INCIDENT_REPORTED, d, e.shipmentId(), e.requesterOrgId(), e.carrierOrgId(), null);
        emit(INCIDENT_REPORTED, d, Recipient.broadcast(OrgType.OPERATOR));
    }

    void onIncidentStatus(@Observes @Outboxed IncidentStatusChanged e) {
        Map<String, Object> d = data("incidentId", e.incidentId(), "shipmentId", e.shipmentId(), "status", e.status());
        emitToShipment(INCIDENT_UPDATED, d, e.shipmentId(), e.requesterOrgId(), e.carrierOrgId(), null);
        emit(INCIDENT_UPDATED, d, Recipient.broadcast(OrgType.OPERATOR));
    }

    // ----- Notificaciones: síncrono en la misma transacción del despacho (ver NotificationPublisher)

    /** V16: avisos por persona → el evento va a esa persona, con su contador de no leídas (y {@code mine}). */
    void onNotifications(@Observes NotificationsPublished e) {
        e.items().forEach(i -> emit(NOTIFICATION_CREATED, data("notificationId", i.notificationId(),
                        "notificationType", i.type(), "relatedType", i.relatedType(), "relatedId", i.relatedId(),
                        "unreadCount", i.unreadCount(), "mine", i.mine()),
                i.profileId() == null ? Recipient.org(i.orgId()) : Recipient.user(i.orgId(), i.profileId())));
    }

    void onReview(@Observes @Outboxed ReviewSubmitted e) {
        emit(REVIEW_SUBMITTED, data("reviewId", e.reviewId(), "shipmentId", e.shipmentId()),
                Recipient.org(e.reviewedOrgId()));
    }

    // ----- Viajes y reservas

    void onBookingRequested(@Observes @Outboxed TripBookingRequested e) {
        booking(e.bookingId(), e.tripId(), null, "PENDING", e.requesterOrgId(), e.carrierOrgId());
    }

    void onBookingConfirmed(@Observes @Outboxed TripBookingConfirmed e) {
        booking(e.bookingId(), e.tripId(), e.shipmentId(), "CONFIRMED", e.requesterOrgId(), e.carrierOrgId());
    }

    void onBookingRejected(@Observes @Outboxed TripBookingRejected e) {
        booking(e.bookingId(), e.tripId(), null, "REJECTED", e.requesterOrgId(), e.carrierOrgId());
    }

    void onBookingCancelled(@Observes @Outboxed TripBookingCancelled e) {
        booking(e.bookingId(), e.tripId(), null, "CANCELLED", e.requesterOrgId(), e.carrierOrgId());
    }

    void onTripCancelled(@Observes @Outboxed TripCancelled e) {
        emit(TRIP_CANCELLED, data("tripId", e.tripId()), Recipient.org(e.carrierOrgId()));
        if (e.affected() != null) {
            e.affected().forEach(a -> emit(TRIP_CANCELLED, data("tripId", e.tripId(), "bookingId", a.bookingId()),
                    Recipient.org(a.requesterOrgId())));
        }
    }

    void onSmartLoadSuggested(@Observes @Outboxed SmartLoadSuggested e) {
        emit(SMART_LOAD_SUGGESTED, data("suggestionId", e.suggestionId(), "itemCount", e.itemCount()),
                Recipient.org(e.carrierOrgId()));
    }

    void onSmartLoadAccepted(@Observes @Outboxed SmartLoadAccepted e) {
        emit(SMART_LOAD_ACCEPTED, data("suggestionId", e.suggestionId()), Recipient.org(e.carrierOrgId()));
        if (e.items() != null) {
            e.items().forEach(i -> emit(SMART_LOAD_ACCEPTED, data("requestId", i.requestId()),
                    Recipient.org(i.requesterOrgId())));
        }
    }

    // ----- helpers

    private void booking(UUID bookingId, UUID tripId, UUID shipmentId, String status, UUID requester, UUID carrier) {
        emit(TRIP_BOOKING_UPDATED, data("bookingId", bookingId, "tripId", tripId, "shipmentId", shipmentId,
                "status", status), orgs(requester, carrier));
    }

    /** Las dos partes del envío y su conductor (dirigido a su usuario). */
    private void emitToShipment(String type, Map<String, Object> d, UUID shipmentId, UUID requester, UUID carrier,
                                UUID knownDriver) {
        List<Recipient> to = new ArrayList<>(List.of(orgs(requester, carrier)));
        UUID driver = knownDriver != null ? knownDriver : drivers.driverProfileOf(shipmentId).orElse(null);
        if (driver != null && carrier != null) to.add(Recipient.user(carrier, driver));
        emit(type, d, to.toArray(Recipient[]::new));
    }

    private static Recipient[] orgs(UUID... ids) {
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID id : ids) if (id != null) unique.add(id);
        return unique.stream().map(Recipient::org).toArray(Recipient[]::new);
    }

    private void emit(String type, Map<String, Object> d, Recipient... to) {
        Set<Recipient> unique = new LinkedHashSet<>(List.of(to));
        unique.forEach(r -> events.append(type, r, d));
    }

    /** Pares clave/valor sin nulos; UUID y enums como texto. */
    static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v == null) continue;
            m.put((String) kv[i], v instanceof UUID || v instanceof Enum<?> ? v.toString() : v);
        }
        return m;
    }
}
