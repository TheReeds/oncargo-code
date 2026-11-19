package pe.logyx.settlement.domain;

import pe.logyx.settlement.domain.model.CommissionStatus;
import pe.logyx.settlement.domain.model.PaymentMethod;
import pe.logyx.settlement.domain.model.Settlement;
import pe.logyx.settlement.domain.model.SettlementStatus;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reglas del cobro de un envío (roadmap 2.12, ADR-0014), en Java puro:
 * <ul>
 *   <li>Solo envíos <b>entregados</b> tienen cobro (LOGYX-STL-001).</li>
 *   <li><b>Transportista</b> (oficina, no conductor): registra su comprobante (PENDING_INVOICE o INVOICED → INVOICED,
 *       corrige mientras la PYME no pague) y confirma que recibió el pago (PAID → CONFIRMED).</li>
 *   <li><b>PYME</b>: registra el pago fuera de la plataforma (PENDING_INVOICE o INVOICED → PAID) con medio, número de
 *       operación (salvo efectivo), fecha y monto; constancia opcional.</li>
 *   <li>Facturar o pagar exige que no haya <b>ajustes pendientes</b> (LOGYX-STL-003): el monto queda fijo. Desde
 *       INVOICED los ajustes del envío se bloquean ({@link #locksAdjustments}).</li>
 *   <li>Cualquiera de las dos partes abre una <b>disputa</b> con motivo desde INVOICED o PAID (LOGYX-STL-004); el
 *       operador la resuelve eligiendo el estado al que vuelve, o quien la abrió la retira (vuelve al anterior).</li>
 *   <li>La <b>comisión</b> la cobra el operador (COLLECTED) en cualquier estado salvo disputa (LOGYX-STL-006).</li>
 *   <li>Vencimiento: entrega + plazo de pago de la PYME ({@link #dueOn}); recordatorios {@link #noticeFor}.</li>
 * </ul>
 * Transición no permitida: LOGYX-STL-002 (409). Lado equivocado: LOGYX-STL-005 (403).
 */
public final class SettlementPolicy {

    /** Quién actúa. */
    public enum Side { REQUESTER, CARRIER, OPERATOR, OTHER }

    /** Recordatorio de pago del barrido diario. */
    public enum Notice { DUE_SOON, DUE_TODAY, OVERDUE }

    public static final int DUE_SOON_DAYS = 3;
    public static final int MAX_TEXT = 40;
    private static final Pattern DOC_NUMBER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ./-]{0,39}");

    private SettlementPolicy() {}

    public static LocalDate dueOn(LocalDate deliveredOn, int termsDays) {
        return deliveredOn.plusDays(Math.max(0, termsDays));
    }

    /** Días de atraso (0 si aún no vence o ya se pagó). */
    public static int daysOverdue(Settlement s, LocalDate today) {
        if (!s.status().awaitingPayment() || !today.isAfter(s.dueOn())) return 0;
        return (int) (today.toEpochDay() - s.dueOn().toEpochDay());
    }

    /** Desde que el transportista factura, el precio no cambia: no se proponen ni deciden ajustes. */
    public static boolean locksAdjustments(SettlementStatus status) {
        return status != null && status != SettlementStatus.PENDING_INVOICE;
    }

    /** El recordatorio que toca hoy, si alguno (y aún no se envió: lo decide quien llama). */
    public static Notice noticeFor(Settlement s, LocalDate today) {
        return noticeFor(s.status(), s.dueOn(), today);
    }

    public static Notice noticeFor(SettlementStatus status, LocalDate dueOn, LocalDate today) {
        if (status == null || !status.awaitingPayment()) return null;
        long left = dueOn.toEpochDay() - today.toEpochDay();
        if (left < 0) return Notice.OVERDUE;
        if (left == 0) return Notice.DUE_TODAY;
        if (left <= DUE_SOON_DAYS) return Notice.DUE_SOON;
        return null;
    }

    public static void invoice(Settlement s, Side side, boolean driver, String number, LocalDate issuedOn,
                               LocalDate deliveredOn, UUID documentId, BigDecimal finalPrice, long pendingAdjustments,
                               LocalDate today, OffsetDateTime now, UUID by) {
        if (side != Side.CARRIER || driver) {
            throw forbidden("El comprobante lo registra la oficina del transportista");
        }
        if (s.status() != SettlementStatus.PENDING_INVOICE && s.status() != SettlementStatus.INVOICED) {
            throw invalid("El comprobante ya no se puede cambiar: " + s.status().label.toLowerCase());
        }
        noPendingAdjustments(pendingAdjustments);
        String n = text(number, "invoiceNumber", "el número del comprobante");
        if (!DOC_NUMBER.matcher(n).matches()) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "El número admite letras, números, guion y punto (p. ej. F001-000123)", "invoiceNumber");
        }
        if (issuedOn == null || issuedOn.isAfter(today) || issuedOn.isBefore(deliveredOn.minusDays(30))) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "La fecha de emisión va desde 30 días antes de la entrega hasta hoy", "issuedOn");
        }
        s.invoice(new Settlement.Invoice(n, issuedOn, documentId, finalPrice, now, by));
    }

    public static void pay(Settlement s, Side side, PaymentMethod method, String operation, LocalDate paidOn,
                           BigDecimal amount, LocalDate deliveredOn, UUID documentId, long pendingAdjustments,
                           LocalDate today, OffsetDateTime now, UUID by) {
        if (side != Side.REQUESTER) throw forbidden("El pago lo registra la PYME del envío");
        if (!s.status().awaitingPayment()) {
            throw invalid("El pago ya está registrado o el cobro está en disputa");
        }
        noPendingAdjustments(pendingAdjustments);
        if (method == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el medio de pago", "method");
        String op = operation == null || operation.isBlank() ? null : operation.trim();
        if (method.requiresOperationNumber()) {
            op = text(operation, "operationNumber", "el número de operación");
            if (!DOC_NUMBER.matcher(op).matches()) {
                throw BusinessException.badRequest("LOGYX-VAL-001",
                        "El número de operación admite letras, números, guion y punto", "operationNumber");
            }
        }
        if (paidOn == null || paidOn.isAfter(today) || paidOn.isBefore(deliveredOn.minusDays(60))) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "La fecha del pago va desde 60 días antes de la entrega hasta hoy", "paidOn");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("100000000")) > 0) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el monto pagado, con hasta 2 decimales", "amount");
        }
        s.pay(new Settlement.Payment(method, op, paidOn, amount, documentId, now, by));
    }

    public static void confirm(Settlement s, Side side, boolean driver, OffsetDateTime now, UUID by) {
        if (side != Side.CARRIER || driver) throw forbidden("Confirma el pago la oficina del transportista");
        if (s.status() != SettlementStatus.PAID) throw invalid("Solo se confirma un pago registrado por la PYME");
        s.confirm(now, by);
    }

    public static void dispute(Settlement s, Side side, boolean driver, String reason, UUID orgId, OffsetDateTime now) {
        if ((side != Side.REQUESTER && side != Side.CARRIER) || driver) {
            throw forbidden("Una disputa la abre una de las partes del envío");
        }
        if (s.status() != SettlementStatus.INVOICED && s.status() != SettlementStatus.PAID) {
            throw BusinessException.conflict("LOGYX-STL-004",
                    "Solo se disputa un cobro facturado o pagado y aún no confirmado");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 5 || why.length() > 500) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Explica el motivo (5 a 500 caracteres)", "reason");
        }
        s.openDispute(new Settlement.Dispute(now, orgId, why, s.status()));
    }

    /**
     * Cierra la disputa. El operador elige a qué estado vuelve ({@code target}; nulo = el anterior); la parte que la
     * abrió solo la retira (vuelve al anterior).
     */
    public static SettlementStatus resolve(Settlement s, Side side, UUID orgId, SettlementStatus target) {
        if (s.status() != SettlementStatus.DISPUTED || s.dispute() == null) throw invalid("El cobro no está en disputa");
        SettlementStatus previous = s.dispute().previous();
        SettlementStatus to;
        if (side == Side.OPERATOR) {
            to = target == null ? previous : target;
            if (to == SettlementStatus.DISPUTED) throw invalid("Elige el estado al que vuelve el cobro");
            if (to == SettlementStatus.INVOICED && s.invoice() == null) throw invalid("No hay comprobante registrado");
            if ((to == SettlementStatus.PAID || to == SettlementStatus.CONFIRMED) && s.payment() == null) {
                throw invalid("No hay un pago registrado");
            }
        } else if ((side == Side.REQUESTER || side == Side.CARRIER) && orgId.equals(s.dispute().byOrgId())) {
            if (target != null && target != previous) {
                throw forbidden("Solo el operador decide otro estado; tú puedes retirar la disputa");
            }
            to = previous;
        } else {
            throw forbidden("La disputa la resuelve el operador o la retira quien la abrió");
        }
        s.closeDispute(to);
        return to;
    }

    public static void ensureCommissionCollectable(Settlement s, BigDecimal commissionWithVat) {
        if (s.commissionStatus() != CommissionStatus.PENDING || commissionWithVat.signum() <= 0) {
            throw BusinessException.conflict("LOGYX-STL-006",
                    "La comisión de este envío ya se cobró o no aplica");
        }
        if (s.status() == SettlementStatus.DISPUTED) {
            throw BusinessException.conflict("LOGYX-STL-006", "El cobro de este envío está en disputa");
        }
    }

    private static void noPendingAdjustments(long pending) {
        if (pending > 0) {
            throw BusinessException.conflict("LOGYX-STL-003",
                    "Hay ajustes del precio pendientes: resuélvelos antes de facturar o pagar");
        }
    }

    private static String text(String raw, String field, String what) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.length() > MAX_TEXT) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica " + what + " (hasta " + MAX_TEXT + " caracteres)", field);
        }
        return v;
    }

    private static BusinessException invalid(String message) {
        return BusinessException.conflict("LOGYX-STL-002", message);
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException(403, "LOGYX-STL-005", message);
    }
}
