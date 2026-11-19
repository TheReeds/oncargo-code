package pe.logyx.settlement.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recordatorio de pago a la PYME (2.12, barrido diario): {@code kind} = {@code DUE_SOON} (vence en 3 días),
 * {@code DUE_TODAY} o {@code OVERDUE} (venció hace {@code daysOverdue} días). {@code amount}: precio final con IGV.
 */
public record PaymentDue(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, String code, String route,
                         String kind, LocalDate dueOn, int daysOverdue, BigDecimal amount) implements OutboxEvent {}
