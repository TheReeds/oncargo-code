package pe.logyx.notification.application;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import pe.logyx.shared.outbox.Outboxed;
import jakarta.inject.Inject;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.incident.application.event.IncidentReported;
import pe.logyx.incident.application.event.IncidentStatusChanged;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.marketplace.application.event.BidAccepted;
import pe.logyx.marketplace.application.event.BidPlaced;
import pe.logyx.negotiation.application.event.CounterOfferUpdated;
import pe.logyx.negotiation.application.event.MessagePosted;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.notification.domain.model.NotificationTarget;
import pe.logyx.notification.domain.model.NotificationType;
import pe.logyx.reputation.application.event.ReviewSubmitted;
import pe.logyx.shared.domain.Money;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shipment.application.event.ShipmentStatusChanged;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.trip.application.event.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Traduce eventos de dominio de otros módulos en notificaciones (ADR-0004). Los módulos que publican no conocen
 * a notification: solo disparan {@code Event<...>}. Los eventos llegan por el outbox transaccional
 * ({@code @Outboxed}, ADR-0006): se notifica solo lo confirmado, dentro de la transacción del despacho, y si algo
 * falla el evento se reintenta (no se pierde).
 */
@ApplicationScoped
public class NotificationObservers {

    @Inject NotificationPublisher publisher;
    @Inject OrganizationDirectory organizations;
    @Inject SystemTx system;

    void onBidPlaced(@Observes @Outboxed BidPlaced e) {
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.NEW_BID, "Nueva oferta recibida",
                e.carrierName() + " ofertó " + Money.soles(e.amount()) + " por tu carga " + e.route() + ".",
                NotificationTarget.REQUEST, e.requestId())));
    }

    void onBidAccepted(@Observes @Outboxed BidAccepted e) {
        List<Notification> out = new ArrayList<>();
        out.add(Notification.to(e.carrierOrgId(), NotificationType.BID_ACCEPTED,
                e.byOperator() ? "LOGYX te asignó una carga" : "¡Tu oferta fue aceptada!",
                (e.byOperator() ? "El operador te asignó la carga " : "Ganaste la carga ") + e.route() + " por "
                        + Money.soles(e.amount()) + ". Asigna un conductor para el recojo.",
                NotificationTarget.SHIPMENT, e.shipmentId()));
        if (e.byOperator()) {
            out.add(Notification.to(e.requesterOrgId(), NotificationType.SHIPMENT_STATUS_CHANGE, "Transportista asignado",
                    "Tu carga " + e.route() + " no recibió ofertas en 24 h y el operador LOGYX asignó un "
                            + "transportista por " + Money.soles(e.amount()) + ".",
                    NotificationTarget.SHIPMENT, e.shipmentId()));
        }
        e.rejected().forEach(r -> out.add(Notification.to(r.carrierOrgId(), NotificationType.BID_REJECTED,
                "Tu oferta no fue elegida", "La carga " + e.route() + " se adjudicó a otro transportista.",
                NotificationTarget.REQUEST, e.requestId())));
        publisher.publish(out);
    }

    void onCounterOffer(@Observes @Outboxed CounterOfferUpdated e) {
        Notification n = switch (e.status()) {
            case PENDING -> Notification.to(e.carrierOrgId(), NotificationType.COUNTER_OFFER, "Nueva contra-oferta",
                    "La PYME propone " + Money.soles(e.amount()) + " por tu oferta. Acéptala o recházala.",
                    NotificationTarget.COUNTER_OFFER, e.counterOfferId());
            case ACCEPTED -> Notification.to(e.requesterOrgId(), NotificationType.COUNTER_OFFER, "Contra-oferta aceptada",
                    "El transportista aceptó " + Money.soles(e.amount()) + ". Ya puedes aceptar la oferta.",
                    NotificationTarget.BID, e.bidId());
            case REJECTED -> Notification.to(e.requesterOrgId(), NotificationType.COUNTER_OFFER, "Contra-oferta rechazada",
                    "El transportista rechazó tu contra-oferta de " + Money.soles(e.amount()) + ".",
                    NotificationTarget.BID, e.bidId());
            case EXPIRED -> null;
        };
        if (n != null) publisher.publish(List.of(n));
    }

    void onMessage(@Observes @Outboxed MessagePosted e) {
        publisher.publish(List.of(Notification.to(e.recipientOrgId(), NotificationType.NEW_MESSAGE,
                "Nuevo mensaje de " + e.senderName(), e.preview(), NotificationTarget.REQUEST, e.requestId())));
    }

    void onShipmentStatus(@Observes @Outboxed ShipmentStatusChanged e) {
        List<Notification> out = new ArrayList<>();
        if (e.status() == Shipment.Status.IN_TRANSIT) {
            out.add(Notification.to(e.requesterOrgId(), NotificationType.SHIPMENT_STATUS_CHANGE, "Tu carga está en camino",
                    "El conductor recogió la carga " + e.route() + ".", NotificationTarget.SHIPMENT, e.shipmentId()));
        } else if (e.status() == Shipment.Status.DELIVERED) {
            out.add(Notification.to(e.requesterOrgId(), NotificationType.SHIPMENT_STATUS_CHANGE, "Carga entregada",
                    "Tu carga " + e.route() + " fue entregada. Revisa la evidencia y califica al transportista.",
                    NotificationTarget.SHIPMENT, e.shipmentId()));
            out.add(Notification.to(e.carrierOrgId(), NotificationType.SHIPMENT_STATUS_CHANGE, "Entrega completada",
                    "Se completó la entrega " + e.route() + ". Ya puedes calificar a la PYME.",
                    NotificationTarget.SHIPMENT, e.shipmentId()));
        }
        publisher.publish(out);
    }

    void onIncidentReported(@Observes @Outboxed IncidentReported e) {
        UUID other = e.reportedByOrgId().equals(e.requesterOrgId()) ? e.carrierOrgId() : e.requesterOrgId();
        String text = "Se reportó una incidencia (" + label(e.category()) + ") en el envío " + e.route()
                + ". El avance de paradas queda en pausa hasta que el operador la resuelva.";
        List<Notification> out = new ArrayList<>();
        out.add(Notification.to(other, NotificationType.INCIDENT_REPORTED, "Incidencia reportada", text,
                NotificationTarget.INCIDENT, e.incidentId()));
        operators().forEach(op -> out.add(Notification.to(op, NotificationType.INCIDENT_REPORTED,
                "Nueva incidencia por revisar", text, NotificationTarget.INCIDENT, e.incidentId())));
        publisher.publish(out);
    }

    void onIncidentStatus(@Observes @Outboxed IncidentStatusChanged e) {
        if (e.status().isActive()) return;   // IN_REVIEW no se notifica
        String title = e.status() == IncidentStatus.RESOLVED ? "Incidencia resuelta" : "Incidencia cerrada";
        String body = (e.resolutionNotes() == null ? "El operador cerró la incidencia." : e.resolutionNotes())
                + " El envío puede continuar.";
        publisher.publish(List.of(
                Notification.to(e.requesterOrgId(), NotificationType.INCIDENT_RESOLVED, title, body, NotificationTarget.INCIDENT,
                        e.incidentId()),
                Notification.to(e.carrierOrgId(), NotificationType.INCIDENT_RESOLVED, title, body, NotificationTarget.INCIDENT,
                        e.incidentId())));
    }

    void onReview(@Observes @Outboxed ReviewSubmitted e) {
        publisher.publish(List.of(Notification.to(e.reviewedOrgId(), NotificationType.NEW_REVIEW, "Nueva calificación",
                e.reviewerName() + " te calificó con " + e.rating().stripTrailingZeros().toPlainString()
                        + " de 5.", NotificationTarget.SHIPMENT, e.shipmentId())));
    }

    // ----- Viajes publicados y reservas (módulo trip)

    void onTripBookingRequested(@Observes @Outboxed TripBookingRequested e) {
        publisher.publish(List.of(Notification.to(e.carrierOrgId(), NotificationType.TRIP_BOOKING_REQUESTED,
                "Nueva reserva en tu viaje", e.requesterName() + " pidió reservar " + kg(e.weightKg()) + " kg en tu viaje "
                        + e.route() + " por " + Money.soles(e.price()) + ". Confírmala o recházala.",
                NotificationTarget.TRIP, e.tripId())));
    }

    void onTripBookingConfirmed(@Observes @Outboxed TripBookingConfirmed e) {
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.TRIP_BOOKING_CONFIRMED,
                "Reserva confirmada", e.carrierName() + " confirmó tu reserva en el viaje " + e.route() + " por "
                        + Money.soles(e.price()) + ". Ya puedes seguir tu envío.",
                NotificationTarget.SHIPMENT, e.shipmentId())));
    }

    void onTripBookingRejected(@Observes @Outboxed TripBookingRejected e) {
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.TRIP_BOOKING_REJECTED,
                "Reserva rechazada", e.carrierName() + " no pudo aceptar tu reserva en el viaje " + e.route()
                        + (e.reason() == null ? "." : ": " + e.reason() + "."),
                NotificationTarget.TRIP, e.tripId())));
    }

    void onTripBookingCancelled(@Observes @Outboxed TripBookingCancelled e) {
        publisher.publish(List.of(Notification.to(e.carrierOrgId(), NotificationType.TRIP_BOOKING_CANCELLED,
                "Reserva cancelada", e.requesterName() + " canceló su reserva en tu viaje " + e.route()
                        + (e.wasConfirmed() ? ". El espacio vuelve a estar disponible." : "."),
                NotificationTarget.TRIP, e.tripId())));
    }

    void onTripCancelled(@Observes @Outboxed TripCancelled e) {
        publisher.publish(e.affected().stream().map(a -> Notification.to(a.requesterOrgId(),
                NotificationType.TRIP_CANCELLED, "Viaje cancelado", e.carrierName() + " canceló el viaje " + e.route()
                        + ". Tu reserva " + (a.wasConfirmed() ? "confirmada " : "") + "quedó cancelada; busca otro viaje o publica tu carga.",
                NotificationTarget.TRIP, e.tripId())).toList());
    }

    void onSmartLoadSuggested(@Observes @Outboxed SmartLoadSuggested e) {
        publisher.publish(List.of(Notification.to(e.carrierOrgId(), NotificationType.SMART_LOAD_SUGGESTED,
                "Nueva carga combinada sugerida", "Combina " + e.itemCount() + " cargas en el vehículo " + e.vehiclePlate()
                        + ": ocupación " + e.occupancyPct().stripTrailingZeros().toPlainString() + " % e ingreso estimado "
                        + Money.soles(e.combinedIncome()) + ".",
                NotificationTarget.SMART_LOAD, e.suggestionId())));
    }

    void onSmartLoadAccepted(@Observes @Outboxed SmartLoadAccepted e) {
        publisher.publish(e.items().stream().map(i -> Notification.to(i.requesterOrgId(),
                NotificationType.SMART_LOAD_ACCEPTED, "Tu carga fue agrupada",
                e.carrierName() + " incluyó tu carga " + i.route() + " en un plan de carga combinada con otras cargas. "
                        + "Es posible que reciba tu carga con una oferta.",
                NotificationTarget.REQUEST, i.requestId())).toList());
    }

    private static String kg(java.math.BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** Organizaciones operadoras (SystemTx: se une a la transacción de sistema del despacho del outbox). */
    private List<UUID> operators() {
        return system.call(() -> organizations.idsOfType(OrgType.OPERATOR));
    }

    private static String label(IncidentCategory c) {
        return switch (c) {
            case CARGO_DAMAGE -> "carga dañada";
            case DELAY -> "retraso";
            case LOST_CARGO -> "extravío";
            case WRONG_DELIVERY -> "entrega incorrecta";
            case DRIVER_CONDUCT -> "conducta del conductor";
            case OTHER -> "otro";
        };
    }
}
