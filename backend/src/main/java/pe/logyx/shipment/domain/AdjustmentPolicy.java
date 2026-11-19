package pe.logyx.shipment.domain;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.shared.domain.Money;
import pe.logyx.shipment.domain.model.AdjustmentType;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;

/**
 * Reglas de los ajustes del envío (roadmap 2.6, ADR-0012):
 * <ul>
 *   <li>Se proponen sobre un envío no cancelado (LOGYX-ADJ-001) mientras está en curso o hasta
 *       {@link #MAX_DAYS_AFTER_DELIVERY} días después de entregado (LOGYX-ADJ-002).</li>
 *   <li>Monto con IGV, distinto de 0, con 2 decimales, cuyo valor absoluto no supera el precio acordado; solo
 *       {@code WEIGHT_DIFF} y {@code OTHER} admiten descuentos (LOGYX-ADJ-003, {@code amount}).</li>
 *   <li>Nota: obligatoria en {@code OTHER} y al rechazar (LOGYX-ADJ-004); sin datos de contacto (RN07: la lee la
 *       contraparte).</li>
 *   <li>Como mucho {@link #MAX_PER_SHIPMENT} ajustes por envío contando todos los estados (LOGYX-ADJ-005).</li>
 *   <li>Decide la PYME mientras no se escale; escalado (pasó {@code respondBy} sin respuesta), decide solo el operador
 *       (LOGYX-ADJ-006, 403).</li>
 * </ul>
 * El precio final = acordado + aceptados ({@link #finalPrice}); RN03 no aplica (el piso es de la oferta).
 */
public final class AdjustmentPolicy {

    public static final int MAX_PER_SHIPMENT = 10;
    public static final int MAX_DAYS_AFTER_DELIVERY = 7;

    /** Quién intenta decidir. */
    public enum Decider { REQUESTER, OPERATOR, OTHER }

    private AdjustmentPolicy() {}

    /** Valida la propuesta y devuelve la nota limpia (o null). */
    public static String validateProposal(Shipment s, AdjustmentType type, BigDecimal amount, String note,
                                          int existingCount, OffsetDateTime now) {
        if (s.status() == Shipment.Status.CANCELLED) {
            throw BusinessException.conflict("LOGYX-ADJ-001", "El envío está cancelado: no admite ajustes");
        }
        if (s.status() == Shipment.Status.DELIVERED && s.deliveredAt() != null
                && s.deliveredAt().plusDays(MAX_DAYS_AFTER_DELIVERY).isBefore(now)) {
            throw BusinessException.conflict("LOGYX-ADJ-002", "Los ajustes se proponen hasta "
                    + MAX_DAYS_AFTER_DELIVERY + " días después de la entrega");
        }
        if (existingCount >= MAX_PER_SHIPMENT) {
            throw BusinessException.conflict("LOGYX-ADJ-005", "Este envío ya tiene " + MAX_PER_SHIPMENT + " ajustes");
        }
        if (amount == null || amount.signum() == 0 || amount.stripTrailingZeros().scale() > 2) {
            throw BusinessException.badRequest("LOGYX-ADJ-003", "Indica un monto distinto de 0, con hasta 2 decimales",
                    "amount");
        }
        if (amount.signum() < 0 && !type.allowsDiscount()) {
            throw BusinessException.badRequest("LOGYX-ADJ-003",
                    "Solo el peso real u «otro» pueden bajar el precio", "amount");
        }
        if (s.agreedPrice() != null && amount.abs().compareTo(s.agreedPrice()) > 0) {
            throw BusinessException.badRequest("LOGYX-ADJ-003", "El ajuste no puede superar el precio acordado ("
                    + Money.soles(s.agreedPrice()) + ")", "amount");
        }
        String clean = note == null || note.isBlank() ? null : ContactInfoPolicy.clean(note, "note");   // RN07
        if (type == AdjustmentType.OTHER && clean == null) {
            throw BusinessException.badRequest("LOGYX-ADJ-004", "Explica el ajuste en la nota", "note");
        }
        return clean;
    }

    /** Nota de la decisión: obligatoria al rechazar (LOGYX-ADJ-004, {@code note}); RN07. */
    public static String decisionNote(boolean accept, String note) {
        String clean = note == null || note.isBlank() ? null : ContactInfoPolicy.clean(note, "note");
        if (!accept && clean == null) {
            throw BusinessException.badRequest("LOGYX-ADJ-004", "Indica por qué rechazas el ajuste", "note");
        }
        return clean;
    }

    /** La PYME decide antes de que se escale; luego, el operador. */
    public static void ensureCanDecide(ShipmentAdjustment a, Decider who) {
        boolean ok = a.isEscalated() ? who == Decider.OPERATOR : who == Decider.REQUESTER;
        if (!ok) {
            throw new BusinessException(403, "LOGYX-ADJ-006", a.isEscalated()
                    ? "Pasó el plazo de respuesta: este ajuste lo decide el operador"
                    : "Este ajuste lo decide la PYME del envío");
        }
    }

    /** Pendiente con el plazo vencido y aún sin escalar. */
    public static boolean dueForEscalation(ShipmentAdjustment a, OffsetDateTime now) {
        return a.isPending() && !a.isEscalated() && !a.respondBy().isAfter(now);
    }

    /** Suma de los aceptados (0 si no hay). */
    public static BigDecimal acceptedTotal(Collection<ShipmentAdjustment> list) {
        return list.stream().filter(ShipmentAdjustment::counts).map(ShipmentAdjustment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** Precio final = acordado + aceptados (nunca negativo). */
    public static BigDecimal finalPrice(BigDecimal agreed, BigDecimal acceptedTotal) {
        if (agreed == null) return null;
        BigDecimal total = agreed.add(acceptedTotal == null ? BigDecimal.ZERO : acceptedTotal);
        return total.signum() < 0 ? BigDecimal.ZERO.setScale(2) : total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Estadía sugerida (criterio 4): si la espera en una parada ({@code completedAt − arrivedAt}) supera la
     * franquicia, se cobra cada media hora iniciada por encima a la tarifa por hora. {@code null} si no corresponde.
     */
    public static DetentionSuggestion detention(OffsetDateTime arrivedAt, OffsetDateTime completedAt, Duration free,
                                                BigDecimal hourlyRate) {
        if (arrivedAt == null || completedAt == null || hourlyRate == null || hourlyRate.signum() <= 0) return null;
        Duration waited = Duration.between(arrivedAt, completedAt);
        if (waited.compareTo(free) <= 0) return null;
        long extraMinutes = waited.minus(free).toMinutes();
        long halfHours = (extraMinutes + 29) / 30;
        BigDecimal hours = BigDecimal.valueOf(halfHours).divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY);
        BigDecimal amount = hourlyRate.multiply(hours).setScale(2, RoundingMode.HALF_UP);
        return new DetentionSuggestion(waited.toMinutes(), hours, amount);
    }

    /** {@code waitedMinutes}: espera total; {@code billableHours}: horas cobrables (en medias horas); {@code amount}. */
    public record DetentionSuggestion(long waitedMinutes, BigDecimal billableHours, BigDecimal amount) {}

    /**
     * Diferencia de peso sugerida: si el peso medido en el recojo supera al declarado en más de 5 %, el ajuste
     * proporcional del precio acordado por el exceso. {@code null} si no corresponde.
     */
    public static BigDecimal weightDifference(BigDecimal declaredKg, BigDecimal measuredKg, BigDecimal agreedPrice) {
        if (declaredKg == null || measuredKg == null || agreedPrice == null || declaredKg.signum() <= 0) return null;
        BigDecimal diff = measuredKg.subtract(declaredKg);
        if (diff.signum() <= 0 || diff.compareTo(declaredKg.multiply(new BigDecimal("0.05"))) <= 0) return null;
        BigDecimal amount = agreedPrice.multiply(diff).divide(declaredKg, 2, RoundingMode.HALF_UP);
        return amount.min(agreedPrice);
    }
}
