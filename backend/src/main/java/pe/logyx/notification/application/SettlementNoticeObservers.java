package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.notification.domain.model.NotificationTarget;
import pe.logyx.notification.domain.model.NotificationType;
import pe.logyx.settlement.application.event.CommissionCollected;
import pe.logyx.settlement.application.event.PaymentDue;
import pe.logyx.settlement.application.event.SettlementUpdated;
import pe.logyx.shared.domain.Money;
import pe.logyx.shared.outbox.Outboxed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Avisos de cobros (V24, roadmap 2.12; categoría «Cobros y pagos»). Misma mecánica que {@link NotificationObservers}:
 * llegan por el outbox y se publican por persona según sus preferencias (correo por omisión: sí).
 * <ul>
 *   <li>{@link SettlementUpdated}: a la contraparte de quien actuó; si actuó el operador, a las dos. Nunca la comisión.</li>
 *   <li>{@link PaymentDue}: a la PYME (por vencer, vence hoy, vencido).</li>
 *   <li>{@link CommissionCollected}: solo al transportista (lleva el monto de la comisión; la PYME nunca lo ve).</li>
 * </ul>
 */
@ApplicationScoped
public class SettlementNoticeObservers {

    @Inject NotificationPublisher publisher;

    void onSettlementUpdated(@Observes @Outboxed SettlementUpdated e) {
        List<UUID> to = new ArrayList<>();
        if (e.byOperator()) {
            to.add(e.requesterOrgId());
            to.add(e.carrierOrgId());
        } else if (e.requesterOrgId().equals(e.actorOrgId())) {
            to.add(e.carrierOrgId());
        } else {
            to.add(e.requesterOrgId());
        }
        String code = e.code() == null ? "" : " · " + e.code();
        String route = e.route() == null ? "" : " " + e.route();
        String amount = e.amount() == null ? "" : " de " + Money.soles(e.amount());
        String who = e.byOperator() ? "OnCargo" : e.requesterOrgId().equals(e.actorOrgId()) ? "La PYME" : "El transportista";
        String title;
        String body;
        if ("DISPUTED".equals(e.fromStatus()) && !"DISPUTED".equals(e.toStatus())) {
            title = "Disputa resuelta" + code;
            body = who + " cerró la disputa del cobro del envío" + route + ". El cobro vuelve a «" + label(e.toStatus()) + "».";
        } else {
            switch (e.toStatus()) {
                case "INVOICED" -> {
                    title = "Comprobante emitido" + code;
                    body = "El transportista emitió su comprobante" + amount + " por el envío" + route
                            + ". Registra el pago cuando lo hagas.";
                }
                case "PAID" -> {
                    title = "Pago registrado" + code;
                    body = "La PYME registró el pago" + amount + " del envío" + route
                            + ". Confírmalo cuando lo veas en tu cuenta.";
                }
                case "CONFIRMED" -> {
                    title = "Pago confirmado" + code;
                    body = "El transportista confirmó que recibió el pago del envío" + route + ".";
                }
                case "DISPUTED" -> {
                    title = "Cobro en disputa" + code;
                    body = who + " abrió una disputa por el cobro del envío" + route
                            + (e.note() == null || e.note().isBlank() ? "." : ": «" + e.note().trim() + "».");
                }
                default -> {
                    title = "Cobro actualizado" + code;
                    body = "El cobro del envío" + route + " está en «" + label(e.toStatus()) + "».";
                }
            }
        }
        publisher.publish(to.stream().map(org -> Notification.to(org, NotificationType.SETTLEMENT_UPDATED, title, body,
                NotificationTarget.SHIPMENT, e.shipmentId())).toList());
    }

    void onPaymentDue(@Observes @Outboxed PaymentDue e) {
        String code = e.code() == null ? "" : " · " + e.code();
        String route = e.route() == null ? "" : " " + e.route();
        String amount = e.amount() == null ? "El pago" : "El pago de " + Money.soles(e.amount());
        String title;
        String body;
        switch (e.kind()) {
            case "DUE_TODAY" -> {
                title = "El pago vence hoy" + code;
                body = amount + " al transportista por el envío" + route + " vence hoy.";
            }
            case "OVERDUE" -> {
                title = "Pago vencido" + code;
                body = amount + " al transportista por el envío" + route + " venció hace " + e.daysOverdue()
                        + (e.daysOverdue() == 1 ? " día" : " días") + ".";
            }
            default -> {
                title = "Pago por vencer" + code;
                body = amount + " al transportista por el envío" + route + " vence el " + date(e.dueOn()) + ".";
            }
        }
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.PAYMENT_DUE, title, body,
                NotificationTarget.SHIPMENT, e.shipmentId())));
    }

    void onCommissionCollected(@Observes @Outboxed CommissionCollected e) {
        String shipments = e.shipments() == 1 ? "1 envío" : e.shipments() + " envíos";
        String op = e.operationNumber() == null || e.operationNumber().isBlank() ? "" : " Operación " + e.operationNumber() + ".";
        publisher.publish(List.of(Notification.to(e.carrierOrgId(), NotificationType.COMMISSION_COLLECTED,
                "Comisión registrada", "OnCargo registró el cobro de " + Money.soles(nonNull(e.amount()))
                        + " de comisión (con IGV) por " + shipments + "." + op, null, null)));
    }

    private static String label(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING_INVOICE" -> "Por facturar";
            case "INVOICED" -> "Facturado";
            case "PAID" -> "Pagado";
            case "CONFIRMED" -> "Confirmado";
            case "DISPUTED" -> "En disputa";
            default -> status;
        };
    }

    private static String date(LocalDate d) {
        return d == null ? "plazo acordado" : d.getDayOfMonth() + "/" + d.getMonthValue() + "/" + d.getYear();
    }

    private static BigDecimal nonNull(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
