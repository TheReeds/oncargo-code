package pe.logyx.settlement.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.pricing.application.CommissionService;
import pe.logyx.settlement.application.event.PaymentDue;
import pe.logyx.settlement.application.port.out.SettlementBootstrap;
import pe.logyx.settlement.application.port.out.SettlementReader;
import pe.logyx.settlement.domain.SettlementPolicy;
import pe.logyx.settlement.domain.model.SettlementStatus;
import pe.logyx.shared.config.SystemParameters;
import pe.logyx.shared.outbox.Outboxed;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shipment.application.event.ShipmentStatusChanged;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.LocalDate;
import java.util.Set;

/**
 * Trabajo de sistema del cobro (2.12): abre el cobro al entregarse un envío y envía los recordatorios de pago.
 * Corre sin usuario por {@code SystemTx} (cruza organizaciones; solo lee cobros y escribe la bitácora
 * {@code settlement_notices}, que hace idempotente el barrido entre instancias y reintentos).
 */
@ApplicationScoped
public class SettlementReminders {

    static final int BATCH = 500;

    @Inject SettlementBootstrap bootstrap;
    @Inject SettlementReader reader;
    @Inject SystemParameters parameters;
    @Inject CommissionService commission;
    @Inject SystemTx system;
    @Inject Event<PaymentDue> due;

    /** Al entregarse (por el conductor o el operador) se abre el cobro con su vencimiento. */
    void onDelivered(@Observes @Outboxed ShipmentStatusChanged e) {
        if (e.status() != Shipment.Status.DELIVERED) return;
        // Se une a la transacción de sistema del despacho del outbox.
        system.run(() -> bootstrap.open(e.shipmentId(), parameters.paymentTermsDays()));
    }

    /** Recordatorios de hoy: 3 días antes, el día del vencimiento y al vencer (una vez cada uno). Devuelve cuántos. */
    public int sweep(LocalDate today) {
        return system.call(() -> {
            int terms = parameters.paymentTermsDays();
            bootstrap.open(null, terms);
            var filter = new SettlementReader.Filter(Set.of(SettlementStatus.PENDING_INVOICE.name(),
                    SettlementStatus.INVOICED.name()), null, null, null, null, null, today, terms);
            int sent = 0;
            for (int offset = 0; ; offset += BATCH) {
                var batch = reader.top(filter, offset, BATCH);
                for (var r : batch.rows()) {
                    var notice = SettlementPolicy.noticeFor(SettlementStatus.valueOf(r.status()), r.dueOn(), today);
                    if (notice == null) continue;
                    // Ordenadas por vencimiento: las que vencen después de la ventana ya no generan aviso.
                    if (!bootstrap.recordNotice(r.shipmentId(), notice.name())) continue;
                    var finalPrice = pe.logyx.shipment.domain.AdjustmentPolicy.finalPrice(r.agreedPrice(),
                            r.acceptedAdjustments() == null ? java.math.BigDecimal.ZERO : r.acceptedAdjustments());
                    int late = notice == SettlementPolicy.Notice.OVERDUE
                            ? (int) (today.toEpochDay() - r.dueOn().toEpochDay()) : 0;
                    String route = label(r.originCity()) + " → " + label(r.destinationCity());
                    due.fire(new PaymentDue(r.shipmentId(), r.requesterOrgId(), r.carrierOrgId(), r.code(), route,
                            notice.name(), r.dueOn(), late, finalPrice));
                    sent++;
                }
                if (batch.rows().size() < BATCH) break;
            }
            return sent;
        });
    }

    private static String label(String city) {
        return pe.logyx.catalog.domain.City.parse(city).map(c -> c.label).orElse(city);
    }
}
