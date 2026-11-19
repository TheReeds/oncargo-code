package pe.logyx.fleet.domain;

import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.FleetDocumentType;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de los documentos de flota con vencimiento (roadmap 2.4), Java puro:
 * <ul>
 *   <li>Vigencia: {@link Vigency}; «por vencer» = faltan 30 días o menos.</li>
 *   <li>Avisos a 30 y 7 días y el día del vencimiento ({@link #noticeThreshold}).</li>
 *   <li>Al ofertar: un documento de la flota <b>vencido</b> avisa (LOGYX-FLT-007) o, si el operador lo configuró,
 *       bloquea; uno que <b>vence antes del recojo</b> solo avisa (LOGYX-FLT-008). Quien nunca subió el documento no
 *       recibe aviso: la regla no castiga la adopción gradual.</li>
 * </ul>
 */
public final class FleetDocumentPolicy {

    public static final int WARN_DAYS = 30;
    public static final int URGENT_DAYS = 7;
    /** Tipos del vehículo que se revisan al ofertar (los que tienen vencimiento). */
    public static final List<FleetDocumentType> BID_RELEVANT = List.of(FleetDocumentType.SOAT,
            FleetDocumentType.TECHNICAL_INSPECTION, FleetDocumentType.INSURANCE_POLICY, FleetDocumentType.MTC_PERMIT);

    public enum Vigency { VALID, EXPIRING, EXPIRED, NO_EXPIRY }

    /** Un hallazgo al evaluar un documento. {@code expired}: ya venció (puede bloquear según el parámetro). */
    public record Issue(String code, String message, FleetDocumentType type, LocalDate expiresAt, boolean expired) {}

    private FleetDocumentPolicy() {}

    public static Vigency status(LocalDate expiresAt, LocalDate today) {
        if (expiresAt == null) return Vigency.NO_EXPIRY;
        if (expiresAt.isBefore(today)) return Vigency.EXPIRED;
        return ChronoUnit.DAYS.between(today, expiresAt) <= WARN_DAYS ? Vigency.EXPIRING : Vigency.VALID;
    }

    /** LOGYX-FLT-010 si falta el vencimiento exigido o la fecha es absurda. */
    public static void ensureUploadable(FleetDocumentType type, LocalDate expiresAt, LocalDate today) {
        if (type.requiresExpiry() && expiresAt == null) {
            throw BusinessException.badRequest("LOGYX-FLT-010",
                    "Indica la fecha de vencimiento de «" + type.label() + "»", "expiresAt");
        }
        if (expiresAt != null && expiresAt.isAfter(today.plusYears(15))) {
            throw BusinessException.badRequest("LOGYX-FLT-010", "La fecha de vencimiento no es válida", "expiresAt");
        }
    }

    /** Umbral de aviso que corresponde a {@code daysLeft} días para vencer: 30, 7 o 0 (vence hoy o ya venció). */
    public static Optional<Integer> noticeThreshold(long daysLeft) {
        if (daysLeft <= 0) return Optional.of(0);
        if (daysLeft <= URGENT_DAYS) return Optional.of(URGENT_DAYS);
        if (daysLeft <= WARN_DAYS) return Optional.of(WARN_DAYS);
        return Optional.empty();
    }

    /** Hallazgos de los documentos vigentes de un vehículo frente a la fecha de recojo de una carga. */
    public static List<Issue> bidIssues(List<FleetDocument> currentDocs, LocalDate today, LocalDate pickupDate) {
        List<Issue> out = new ArrayList<>();
        for (FleetDocument d : currentDocs) {
            if (!d.isCurrent() || d.expiresAt() == null || !BID_RELEVANT.contains(d.type())) continue;
            if (d.expiresAt().isBefore(today)) {
                out.add(new Issue("LOGYX-FLT-007", d.type().label() + " vencido el " + d.expiresAt()
                        + ": renuévalo y sube el archivo nuevo", d.type(), d.expiresAt(), true));
            } else if (pickupDate != null && d.expiresAt().isBefore(pickupDate)) {
                out.add(new Issue("LOGYX-FLT-008", d.type().label() + " vence el " + d.expiresAt()
                        + ", antes del recojo", d.type(), d.expiresAt(), false));
            }
        }
        return out;
    }

    /** Vencimiento del sello solo si sigue vigente hoy; nulo si falta o ya venció. */
    public static LocalDate sealValidUntil(List<FleetDocument> currentDocs, FleetDocumentType type, LocalDate today) {
        return currentDocs.stream().filter(d -> d.isCurrent() && d.type() == type && d.expiresAt() != null
                        && !d.expiresAt().isBefore(today))
                .map(FleetDocument::expiresAt).max(LocalDate::compareTo).orElse(null);
    }
}
