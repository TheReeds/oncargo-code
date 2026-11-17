package pe.logyx.reputation.domain.model;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Calificación post-entrega de una parte del envío a la otra (RF40/RF41). Tres dimensiones 1–5; {@code rating} es
 * su promedio. PYME → transportista: puntualidad, estado de la carga, comunicación. Transportista → PYME:
 * puntualidad (carga lista a tiempo), exactitud de la carga declarada, comunicación.
 */
public record Review(UUID id, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID reviewerOrgId,
                     UUID reviewedOrgId, UUID reviewerProfileId, ReviewDirection direction, int punctuality,
                     int cargoCondition, int communication, BigDecimal rating, String comment,
                     OffsetDateTime createdAt) {

    public static Review write(UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, UUID reviewerOrgId,
                               UUID reviewerProfileId, int punctuality, int cargoCondition, int communication,
                               String comment) {
        check(punctuality, "punctuality");
        check(cargoCondition, "cargoCondition");
        check(communication, "communication");
        boolean byPyme = reviewerOrgId.equals(requesterOrgId);
        BigDecimal rating = BigDecimal.valueOf(punctuality + cargoCondition + communication)
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        String clean = ContactInfoPolicy.clean(comment, "comment");          // RN07: la reseña es pública (RF44)
        return new Review(UUID.randomUUID(), shipmentId, requesterOrgId, carrierOrgId, reviewerOrgId,
                byPyme ? carrierOrgId : requesterOrgId, reviewerProfileId,
                byPyme ? ReviewDirection.PYME_TO_CARRIER : ReviewDirection.CARRIER_TO_PYME,
                punctuality, cargoCondition, communication, rating, clean, OffsetDateTime.now());
    }

    private static void check(int value, String field) {
        if (value < 1 || value > 5) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La calificación debe estar entre 1 y 5", field);
        }
    }
}
