package pe.logyx.settlement.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * El cobro de un envío cambió de estado (2.12): facturado, pagado, confirmado, en disputa o resuelto. Aviso a la
 * contraparte de quien actuó ({@code actorOrgId}); si actuó el operador, a las dos. Estados como texto
 * ({@code SettlementStatus}): {@code PENDING_INVOICE}, {@code INVOICED}, {@code PAID}, {@code CONFIRMED},
 * {@code DISPUTED}. {@code amount}: precio final con IGV (o lo pagado al registrar el pago). Nunca la comisión.
 */
public record SettlementUpdated(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, String code, String route,
                                String fromStatus, String toStatus, UUID actorOrgId, boolean byOperator,
                                BigDecimal amount, String note) implements OutboxEvent {}
