package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.alerts.application.LoadAlertMatcher;
import pe.logyx.fleet.application.event.FleetDocumentExpiring;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.marketplace.application.event.BidImproved;
import pe.logyx.marketplace.application.event.RequestPublished;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.notification.domain.model.NotificationTarget;
import pe.logyx.notification.domain.model.NotificationType;
import pe.logyx.shared.domain.Money;
import pe.logyx.shared.outbox.Outboxed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Avisos de la zona del transportista (Ola 2, T): carga nueva que coincide con una búsqueda guardada (2.3), documento
 * de flota por vencer (2.4) y oferta mejorada (2.8, para la PYME). Misma mecánica que {@link NotificationObservers}:
 * llegan por el outbox y se publican por persona según sus preferencias.
 */
@ApplicationScoped
public class CarrierNotificationObservers {

    @Inject NotificationPublisher publisher;
    @Inject LoadAlertMatcher alerts;
    @Inject MarketplaceQueries marketplace;
    @Inject UserAccounts accounts;

    /**
     * 2.3: un aviso por persona cuyas búsquedas coinciden (nombra la primera) y cuyo vehículo puede ofertar.
     * V24 (2.2): además, a cada persona de los transportistas cuya ruta de interés coincide (alerta implícita), salvo a
     * quien ya le llegó por una búsqueda guardada. Cada uno según su preferencia de «Alertas de carga».
     */
    void onRequestPublished(@Observes @Outboxed RequestPublished e) {
        var matches = alerts.matchesFor(e.requestId());
        var routeMatches = alerts.routeMatchesFor(e.requestId());
        if (matches.isEmpty() && routeMatches.isEmpty()) return;
        var r = marketplace.publishedForSystem(e.requestId()).orElse(null);
        if (r == null) return;
        var c = r.cargo();
        String what = c.cargoType().label + " · " + c.weightKg().stripTrailingZeros().toPlainString() + " kg · recojo "
                + c.pickupDate().getDayOfMonth() + "/" + c.pickupDate().getMonthValue();
        List<Notification> out = new ArrayList<>(matches.stream().map(m -> Notification.toPerson(m.organizationId(), m.profileId(),
                NotificationType.NEW_LOAD_MATCH, "Carga nueva: " + e.route(),
                what + ". Coincide con «" + m.searchNames().getFirst() + "»"
                        + (m.searchNames().size() > 1 ? " y " + (m.searchNames().size() - 1) + " más" : "")
                        + ". La subasta ya está abierta.", NotificationTarget.REQUEST, e.requestId())).toList());
        Set<UUID> already = matches.stream().map(LoadAlertMatcher.Match::profileId).collect(Collectors.toSet());
        for (var m : routeMatches) {
            for (Profile p : accounts.noticeRecipients(m.organizationId())) {
                if (already.contains(p.id())) continue;
                out.add(Notification.toPerson(m.organizationId(), p.id(), NotificationType.NEW_LOAD_MATCH,
                        "Carga nueva en tu ruta: " + e.route(),
                        what + ". Es una de las rutas de interés de tu empresa. La subasta ya está abierta.",
                        NotificationTarget.REQUEST, e.requestId(), false));
            }
        }
        publisher.publish(out);
    }

    /** 2.4: a la organización dueña de la flota (cada persona según su preferencia de «Documentos de flota»). */
    void onFleetDocumentExpiring(@Observes @Outboxed FleetDocumentExpiring e) {
        boolean vehicle = "VEHICLE".equals(e.ownerType());
        String title = e.daysLeft() < 0 ? e.docLabel() + " de " + e.ownerLabel() + " venció"
                : e.daysLeft() == 0 ? e.docLabel() + " de " + e.ownerLabel() + " vence hoy"
                : e.docLabel() + " de " + e.ownerLabel() + " vence en " + e.daysLeft() + (e.daysLeft() == 1 ? " día" : " días");
        String body = e.daysLeft() < 0
                ? "Venció el " + e.expiresAt() + ". Sube el documento nuevo en Flota."
                : "Vence el " + e.expiresAt() + ". Sube el documento nuevo en Flota antes de esa fecha.";
        publisher.publish(List.of(Notification.to(e.organizationId(), NotificationType.FLEET_DOCUMENT_EXPIRING, title, body,
                vehicle ? NotificationTarget.VEHICLE : NotificationTarget.DRIVER, e.ownerId())));
    }

    /** 2.8: a la PYME dueña de la carga. */
    void onBidImproved(@Observes @Outboxed BidImproved e) {
        String what = e.vehicleChanged() ? "mejoró su oferta (nuevo monto y vehículo)" : "bajó su oferta";
        publisher.publish(List.of(Notification.to(e.requesterOrgId(), NotificationType.BID_IMPROVED, "Oferta mejorada",
                e.carrierName() + " " + what + " a " + Money.soles(e.amount()) + " por tu carga " + e.route() + ".",
                NotificationTarget.REQUEST, e.requestId())));
    }
}
