package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.incident.application.event.IncidentAssigned;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.notification.domain.model.NotificationTarget;
import pe.logyx.notification.domain.model.NotificationType;
import pe.logyx.shared.domain.Money;
import pe.logyx.shared.outbox.Outboxed;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shipment.application.event.AdjustmentDecided;
import pe.logyx.shipment.application.event.AdjustmentEscalated;
import pe.logyx.shipment.application.event.AdjustmentProposed;
import pe.logyx.shipment.domain.model.AdjustmentStatus;
import pe.logyx.shipment.domain.model.AdjustmentType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Avisos de la Ola 2, zona X (ejecución y operación): ajustes del envío (2.6) e incidencias asignadas (2.7). Aparte de
 * {@link NotificationObservers} para no mezclar zonas; mismas reglas (outbox, textos en español, payload mínimo).
 */
@ApplicationScoped
public class ExecutionNoticeObservers {

    @Inject NotificationPublisher publisher;
    @Inject OrganizationDirectory organizations;
    @Inject SystemTx system;

    void onAdjustmentProposed(@Observes @Outboxed AdjustmentProposed e) {
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.ADJUSTMENT_PROPOSED,
                "Ajuste por revisar", "El transportista propone " + what(e.type()) + " de " + Money.soles(e.amount())
                        + " en el envío " + ref(e.code(), e.route()) + ". Acéptalo o recházalo.",
                NotificationTarget.SHIPMENT, e.shipmentId())));
    }

    void onAdjustmentDecided(@Observes @Outboxed AdjustmentDecided e) {
        String amount = Money.soles(e.amount());
        String ref = ref(e.code(), e.route());
        List<Notification> out = new ArrayList<>();
        if (e.status() == AdjustmentStatus.WITHDRAWN) {
            out.add(Notification.to(e.requesterOrgId(), NotificationType.ADJUSTMENT_DECIDED, "Ajuste retirado",
                    "El transportista retiró " + what(e.type()) + " de " + amount + " en el envío " + ref + ".",
                    NotificationTarget.SHIPMENT, e.shipmentId()));
        } else {
            boolean accepted = e.status() == AdjustmentStatus.ACCEPTED;
            String title = accepted ? "Ajuste aceptado" : "Ajuste rechazado";
            String who = e.byOperator() ? "El operador" : "La PYME";
            String note = e.note() == null || e.note().isBlank() ? ""
                    : " Nota: «" + e.note().strip().replaceAll("[\\s.;,:]+$", "") + "».";
            String body = who + (accepted ? " aceptó " : " rechazó ") + what(e.type()) + " de " + amount
                    + " en el envío " + ref + "." + note;
            out.add(Notification.to(e.carrierOrgId(), NotificationType.ADJUSTMENT_DECIDED, title, body,
                    NotificationTarget.SHIPMENT, e.shipmentId()));
            if (e.byOperator()) {
                out.add(Notification.to(e.requesterOrgId(), NotificationType.ADJUSTMENT_DECIDED, title, body,
                        NotificationTarget.SHIPMENT, e.shipmentId()));
            }
        }
        publisher.publish(out);
    }

    void onAdjustmentEscalated(@Observes @Outboxed AdjustmentEscalated e) {
        String ref = ref(e.code(), e.route());
        String text = "Nadie respondió a tiempo " + what(e.type()) + " de " + Money.soles(e.amount()) + " en el envío "
                + ref + ". Lo decide el operador.";
        List<Notification> out = new ArrayList<>();
        operators().forEach(op -> out.add(Notification.to(op, NotificationType.ADJUSTMENT_ESCALATED,
                "Ajuste por decidir", text, NotificationTarget.SHIPMENT, e.shipmentId())));
        out.add(Notification.to(e.requesterOrgId(), NotificationType.ADJUSTMENT_ESCALATED, "Ajuste en manos del operador",
                text, NotificationTarget.SHIPMENT, e.shipmentId()));
        out.add(Notification.to(e.carrierOrgId(), NotificationType.ADJUSTMENT_ESCALATED, "Ajuste en manos del operador",
                text, NotificationTarget.SHIPMENT, e.shipmentId()));
        publisher.publish(out);
    }

    void onIncidentAssigned(@Observes @Outboxed IncidentAssigned e) {
        publisher.publish(List.of(Notification.toPerson(e.operatorOrgId(), e.assigneeProfileId(),
                NotificationType.INCIDENT_ASSIGNED, "Te asignaron una incidencia",
                "Incidencia de " + e.category() + ". Revisa el detalle y responde dentro del plazo.",
                NotificationTarget.INCIDENT, e.incidentId())));
    }

    private List<UUID> operators() {
        return system.call(() -> organizations.idsOfType(OrgType.OPERATOR));
    }

    private static String ref(String code, String route) {
        if (code == null || code.isBlank()) return route;
        return route == null || route.isBlank() ? code : code + " (" + route + ")";
    }

    /** "un ajuste por peaje", "una estadía"… */
    static String what(AdjustmentType t) {
        return switch (t) {
            case WEIGHT_DIFF -> "un ajuste por peso real";
            case DETENTION -> "una estadía";
            case TOLL -> "un peaje";
            case HANDLING -> "un cobro por maniobras";
            case OTHER -> "un ajuste";
        };
    }
}
