package pe.logyx.reputation.domain;

import pe.logyx.shared.domain.BusinessException;

import java.util.Optional;

/**
 * RN06 / RF45: se califica solo un envío ENTREGADO con al menos una foto de evidencia de entrega, una vez por
 * organización y envío.
 */
public final class ReviewEligibilityPolicy {

    /** Motivo por el que aún no se puede calificar (código de error que devolvería el POST). */
    public record Blocker(String code, String message) {}

    private ReviewEligibilityPolicy() {}

    public static Optional<Blocker> blocker(boolean delivered, boolean hasDeliveryPhoto, boolean alreadyReviewed) {
        if (!delivered) return Optional.of(new Blocker("LOGYX-REV-001", "El envío aún no ha sido entregado (RN06)"));
        if (!hasDeliveryPhoto) {
            return Optional.of(new Blocker("LOGYX-REV-002", "El envío no tiene foto de evidencia de entrega (RN06)"));
        }
        if (alreadyReviewed) return Optional.of(new Blocker("LOGYX-REV-003", "Ya calificaste este envío"));
        return Optional.empty();
    }

    public static void ensureCanReview(boolean delivered, boolean hasDeliveryPhoto, boolean alreadyReviewed) {
        blocker(delivered, hasDeliveryPhoto, alreadyReviewed).ifPresent(b -> {
            throw BusinessException.conflict(b.code(), b.message());
        });
    }
}
