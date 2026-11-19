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
import pe.logyx.shipment.application.event.ShipmentReassigned;
import pe.logyx.identity.application.event.OrganizationVerificationChanged;
import pe.logyx.identity.application.event.AccessRequested;
import pe.logyx.marketplace.application.event.AuctionClosed;
import pe.logyx.marketplace.application.event.BidWithdrawn;
import pe.logyx.marketplace.application.event.RequestCancelled;
import pe.logyx.marketplace.application.event.BackupRequested;
import pe.logyx.marketplace.application.event.BackupResponded;
import pe.logyx.marketplace.application.event.CarriersInvited;
import pe.logyx.templates.application.event.TemplateRunRecorded;
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
        Notification n = e.byCarrier() ? switch (e.status()) {        // RF26: la propuso el transportista
            case PENDING -> Notification.to(e.requesterOrgId(), NotificationType.COUNTER_OFFER, "Nueva contra-oferta",
                    "El transportista propone " + Money.soles(e.amount()) + " por su oferta. Acéptala o recházala.",
                    NotificationTarget.COUNTER_OFFER, e.counterOfferId());
            case ACCEPTED -> Notification.to(e.carrierOrgId(), NotificationType.COUNTER_OFFER, "Contra-oferta aceptada",
                    "La PYME aceptó " + Money.soles(e.amount()) + ". Tu oferta queda en ese monto; la PYME decide si te "
                            + "adjudica.", NotificationTarget.BID, e.bidId());
            case REJECTED -> Notification.to(e.carrierOrgId(), NotificationType.COUNTER_OFFER, "Contra-oferta rechazada",
                    "La PYME rechazó tu contra-oferta de " + Money.soles(e.amount()) + ".",
                    NotificationTarget.BID, e.bidId());
            case EXPIRED -> null;
        } : switch (e.status()) {
            case PENDING -> Notification.to(e.carrierOrgId(), NotificationType.COUNTER_OFFER, "Nueva contra-oferta",
                    "La PYME propone " + Money.soles(e.amount()) + " por tu oferta. Acéptala, recházala o responde con "
                            + "otro monto.", NotificationTarget.COUNTER_OFFER, e.counterOfferId());
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

    // ----- V13: verificación (RN01), subastas sin ofertas (F07), cancelaciones (F06, F16), retiro de oferta (F08)

    void onVerification(@Observes @Outboxed OrganizationVerificationChanged e) {
        String note = e.note() == null ? "" : " Motivo: " + e.note();
        Notification n = switch (e.status()) {
            case VERIFIED -> Notification.to(e.organizationId(), NotificationType.ORGANIZATION_VERIFICATION,
                    "Tu empresa está verificada", "LOGYX verificó tu organización: ya tienes el sello «Verificado» y "
                            + "puedes publicar y ofertar.", NotificationTarget.ORGANIZATION, e.organizationId());
            case REJECTED -> Notification.to(e.organizationId(), NotificationType.ORGANIZATION_VERIFICATION,
                    "Verificación rechazada", "LOGYX no pudo verificar tu organización." + note,
                    NotificationTarget.ORGANIZATION, e.organizationId());
            case SUSPENDED -> Notification.to(e.organizationId(), NotificationType.ORGANIZATION_VERIFICATION,
                    "Organización suspendida", "LOGYX suspendió tu organización: no puedes publicar, ofertar ni "
                            + "reservar." + note, NotificationTarget.ORGANIZATION, e.organizationId());
            case PENDING -> null;
        };
        if (n != null) publisher.publish(List.of(n));
        // V14 (F20): una organización rechazada corrigió su razón social o RUC y vuelve a la bandeja del operador.
        if (e.status() == pe.logyx.identity.domain.model.Organization.VerificationStatus.PENDING) {
            String name = organizations.get(e.organizationId()).legalName();
            publisher.publish(operators().stream().map(op -> Notification.to(op,
                    NotificationType.ORGANIZATION_VERIFICATION, "Organización por verificar de nuevo",
                    name + " corrigió sus datos después del rechazo: revísala en la bandeja de verificación.",
                    NotificationTarget.ORGANIZATION, e.organizationId())).toList());
        }
    }

    /** V16 (roadmap 1.7/1.15): aviso personal a cada ADMIN para que cambie el rol de quien lo pide. */
    void onAccessRequested(@Observes @Outboxed AccessRequested e) {
        String what = switch (e.permission()) {
            case "OPERATE" -> "operar (publicar, ofertar, aceptar y gestionar envíos)";
            case "MANAGE_USERS" -> "gestionar usuarios y roles";
            case "MANAGE_ORGANIZATION" -> "editar los datos de la empresa";
            case "MANAGE_DRIVERS" -> "gestionar las cuentas de conductores";
            default -> "más permisos";
        };
        String why = e.reason() == null ? "" : " Motivo: «" + e.reason() + "».";
        String body = e.requesterName() + " pide permiso para " + what + " (hoy tiene el rol " + e.requesterRole() + ")."
                + why + " Si estás de acuerdo, cámbiale el rol en Usuarios y roles.";
        publisher.publish(e.adminProfileIds().stream().map(admin -> Notification.toPerson(e.organizationId(), admin,
                NotificationType.ACCESS_REQUESTED, "Pedido de acceso de " + e.requesterName(), body,
                NotificationTarget.USER, e.requesterProfileId())).toList());
    }

    void onAuctionClosed(@Observes @Outboxed AuctionClosed e) {
        List<Notification> out = new ArrayList<>();
        if (e.bidCount() == 0) {
            out.add(Notification.to(e.requesterOrgId(), NotificationType.AUCTION_CLOSED, "Tu subasta cerró sin ofertas",
                    "Nadie ofertó por tu carga " + e.route() + ". Puedes reabrir la subasta (y cambiar la fecha de "
                            + "recojo) o esperar: el operador LOGYX puede asignarte un transportista.",
                    NotificationTarget.REQUEST, e.requestId()));
            operators().forEach(op -> out.add(Notification.to(op, NotificationType.AUCTION_CLOSED,
                    "Solicitud sin ofertas", "La subasta de la carga " + e.route() + " cerró sin ofertas: ya puedes "
                            + "asignarla manualmente (RN10).", NotificationTarget.REQUEST, e.requestId())));
        } else {
            out.add(Notification.to(e.requesterOrgId(), NotificationType.AUCTION_CLOSED, "Tu subasta cerró",
                    "La subasta de tu carga " + e.route() + " cerró con " + e.bidCount()
                            + (e.bidCount() == 1 ? " oferta" : " ofertas") + ". Compara y acepta la que prefieras.",
                    NotificationTarget.REQUEST, e.requestId()));
        }
        publisher.publish(out);
    }

    void onRequestCancelled(@Observes @Outboxed RequestCancelled e) {
        String why = e.reason() == null ? "." : ": " + e.reason() + ".";
        publisher.publish(e.carrierOrgIds().stream().map(c -> Notification.to(c, NotificationType.REQUEST_CANCELLED,
                "Solicitud cancelada", "La PYME canceló la carga " + e.route() + why + " Tu oferta quedó sin efecto.",
                NotificationTarget.REQUEST, e.requestId())).toList());
    }

    // ----- Ola 2, zona P (roadmap 2.1 y 2.9)

    /** 2.1: lo que hizo la programación de una plantilla, a quien creó la plantilla. */
    void onTemplateRun(@Observes @Outboxed TemplateRunRecorded e) {
        String when = e.pickupDate() == null ? "" : " para el recojo del " + e.pickupDate();
        String title, body;
        NotificationTarget target = e.requestId() == null ? NotificationTarget.TEMPLATE : NotificationTarget.REQUEST;
        UUID related = e.requestId() == null ? e.templateId() : e.requestId();
        switch (e.kind()) {
            case "PUBLISHED" -> {
                title = "Carga publicada por «" + e.templateName() + "»";
                body = e.requestCode() + " · " + e.route() + when + ". La creó la plantilla, no una persona.";
            }
            case "DRAFT" -> {
                title = "Borrador listo: «" + e.templateName() + "»";
                body = e.route() + when + ". Revísalo y publícalo desde Plantillas.";
            }
            default -> {
                title = "No se pudo publicar «" + e.templateName() + "»";
                body = e.route() + when + ": " + e.message() + (e.paused()
                        ? " La programación se pausó tras varios fallos seguidos." : " No se reintentará.");
            }
        }
        publisher.publish(List.of(Notification.toPerson(e.organizationId(), e.profileId(), NotificationType.TEMPLATE_RUN,
                title, body, target, related)));
    }

    /** 2.9: «invitar a ofertar» a los transportistas preferidos. */
    void onCarriersInvited(@Observes @Outboxed CarriersInvited e) {
        String when = e.pickupDate() == null ? "" : ", recojo el " + e.pickupDate();
        publisher.publish(e.carrierOrgIds().stream().map(c -> Notification.to(c, NotificationType.REQUEST_INVITATION,
                "Te invitan a ofertar", e.requesterName() + " te invita a ofertar por su carga " + e.route() + " ("
                        + e.weightKg().stripTrailingZeros().toPlainString() + " kg" + when + ").",
                NotificationTarget.REQUEST, e.requestId())).toList());
    }

    /** 2.9: la PYME pide al transportista de la segunda oferta que confirme. */
    void onBackupRequested(@Observes @Outboxed BackupRequested e) {
        publisher.publish(List.of(Notification.to(e.carrierOrgId(), NotificationType.BACKUP_REQUESTED,
                "Confirma si puedes llevar la carga", e.requesterName() + " perdió a su transportista en " + e.route()
                        + " y quiere adjudicarte la carga con tu oferta. Confirma si puedes llevarla.",
                NotificationTarget.BID, e.bidId())));
    }

    /** 2.9: el transportista respondió. */
    void onBackupResponded(@Observes @Outboxed BackupResponded e) {
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.BACKUP_RESPONDED,
                e.accepted() ? "Confirmó la segunda oferta" : "No puede llevar la carga",
                e.accepted() ? e.carrierName() + " confirmó que lleva tu carga " + e.route() + ". Ya puedes adjudicársela."
                        : e.carrierName() + " no puede llevar tu carga " + e.route() + ". Prueba con otra oferta.",
                NotificationTarget.REQUEST, e.requestId())));
    }

    void onBidWithdrawn(@Observes @Outboxed BidWithdrawn e) {
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.BID_WITHDRAWN,
                "Oferta retirada", e.carrierName() + " retiró su oferta por tu carga " + e.route() + ".",
                NotificationTarget.REQUEST, e.requestId())));
    }

    void onShipmentReassigned(@Observes @Outboxed ShipmentReassigned e) {
        boolean vehicle = "VEHICLE".equals(e.what());
        String why = e.reason() == null ? "" : " Motivo: " + e.reason();
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.SHIPMENT_STATUS_CHANGE,
                vehicle ? "Cambio de vehículo" : "Cambio de conductor",
                (vehicle ? "Tu carga " + e.route() + " ahora va en el vehículo " + e.vehiclePlate()
                        : "Tu carga " + e.route() + " ahora la lleva " + e.driverName()) + "." + why,
                NotificationTarget.SHIPMENT, e.shipmentId())));
    }

    void onMessage(@Observes @Outboxed MessagePosted e) {
        publisher.publish(List.of(Notification.to(e.recipientOrgId(), NotificationType.NEW_MESSAGE,
                "Nuevo mensaje de " + e.senderName(), e.preview(), NotificationTarget.REQUEST, e.requestId())));
    }

    void onShipmentStatus(@Observes @Outboxed ShipmentStatusChanged e) {
        List<Notification> out = new ArrayList<>();
        if (e.status() == Shipment.Status.CANCELLED) {
            String why = e.reason() == null ? "." : ": " + e.reason() + ".";
            String body = "Se canceló el envío " + e.route() + why;
            if (!e.requesterOrgId().equals(e.actorOrgId())) {
                out.add(Notification.to(e.requesterOrgId(), NotificationType.SHIPMENT_CANCELLED, "Envío cancelado",
                        body + " Puedes republicar tu carga.", NotificationTarget.SHIPMENT, e.shipmentId()));
            }
            if (!e.carrierOrgId().equals(e.actorOrgId())) {
                out.add(Notification.to(e.carrierOrgId(), NotificationType.SHIPMENT_CANCELLED, "Envío cancelado",
                        body, NotificationTarget.SHIPMENT, e.shipmentId()));
            }
        } else if (e.status() == Shipment.Status.IN_TRANSIT) {
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
                + (e.freezes() ? ". El avance de paradas queda en pausa hasta que el operador la resuelva."
                        : ". Es informativa: el viaje sigue.");
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
        String effect = e.shipmentOutcome() == null ? "" : switch (e.shipmentOutcome()) {        // RF55
            case RESUME -> " El envío puede continuar.";
            case DELIVERED -> " El envío se dio por entregado.";
            case CANCELLED -> " El envío quedó cancelado.";
        };
        // F46: las notas van citadas y, sin su puntuación final, se cierran con un solo punto antes del texto fijo.
        String quoted = e.resolutionNotes() == null ? "" : e.resolutionNotes().strip().replaceAll("[\\s.;,:]+$", "");
        String notes = quoted.isEmpty() ? "El operador cerró la incidencia."
                : "Resolución del operador: «" + quoted + "».";
        String body = notes + effect;
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
                NotificationType.SMART_LOAD_ACCEPTED, "Oferta consolidada por tu carga",
                e.carrierName() + " ofertó por tu carga " + i.route() + " dentro de un plan de carga consolidada: "
                        + "viajaría junto con otras cargas compatibles en el mismo vehículo. Tú decides si la aceptas.",
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
        return c.label();
    }
}
