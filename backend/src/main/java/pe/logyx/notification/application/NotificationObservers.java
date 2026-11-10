package pe.logyx.notification.application;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Traduce eventos de dominio de otros módulos en notificaciones (ADR-0004). Los módulos que publican no conocen
 * a notification: solo disparan {@code Event<...>}. Todos los observadores son {@code AFTER_SUCCESS}: se notifica
 * lo que de verdad se confirmó.
 */
@ApplicationScoped
public class NotificationObservers {

    @Inject NotificationPublisher publisher;
    @Inject OrganizationDirectory organizations;
    @Inject SystemTx system;

    void onBidPlaced(@Observes(during = TransactionPhase.AFTER_SUCCESS) BidPlaced e) {
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.NEW_BID, "Nueva oferta recibida",
                e.carrierName() + " ofertó " + Money.soles(e.amount()) + " por tu carga " + e.route() + ".",
                NotificationTarget.REQUEST, e.requestId())));
    }

    void onBidAccepted(@Observes(during = TransactionPhase.AFTER_SUCCESS) BidAccepted e) {
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

    void onCounterOffer(@Observes(during = TransactionPhase.AFTER_SUCCESS) CounterOfferUpdated e) {
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

    void onMessage(@Observes(during = TransactionPhase.AFTER_SUCCESS) MessagePosted e) {
        publisher.publish(List.of(Notification.to(e.recipientOrgId(), NotificationType.NEW_MESSAGE,
                "Nuevo mensaje de " + e.senderName(), e.preview(), NotificationTarget.REQUEST, e.requestId())));
    }

    void onShipmentStatus(@Observes(during = TransactionPhase.AFTER_SUCCESS) ShipmentStatusChanged e) {
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

    void onIncidentReported(@Observes(during = TransactionPhase.AFTER_SUCCESS) IncidentReported e) {
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

    void onIncidentStatus(@Observes(during = TransactionPhase.AFTER_SUCCESS) IncidentStatusChanged e) {
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

    void onReview(@Observes(during = TransactionPhase.AFTER_SUCCESS) ReviewSubmitted e) {
        publisher.publish(List.of(Notification.to(e.reviewedOrgId(), NotificationType.NEW_REVIEW, "Nueva calificación",
                e.reviewerName() + " te calificó con " + e.rating().stripTrailingZeros().toPlainString()
                        + " de 5.", NotificationTarget.SHIPMENT, e.shipmentId())));
    }

    /** Organizaciones operadoras (perfil público orgs_read; en SystemTx porque corre tras el commit). */
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
