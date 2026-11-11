package pe.logyx.reputation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import pe.logyx.shared.outbox.Outboxed;
import jakarta.inject.Inject;
import pe.logyx.reputation.application.event.ReviewSubmitted;
import pe.logyx.reputation.application.port.out.ReputationScoreRepository;
import pe.logyx.reputation.application.port.out.ReviewRepository;
import pe.logyx.reputation.domain.TrustScoreCalculator;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * RN09: recalcula el Trust Score de la organización calificada tras el commit de cada calificación (vía outbox,
 * {@code @Outboxed}: si falla, se reintenta).
 * SystemTx justificado: quien califica escribe la reputación de OTRA organización y reputation_scores no admite
 * escrituras de logyx_app (V6). La fila se bloquea para que recálculos concurrentes no se pisen y se recalcula
 * desde todas las calificaciones confirmadas (no incrementalmente).
 */
@ApplicationScoped
public class ReputationRecalculator {

    @Inject ReviewRepository reviews;
    @Inject ReputationScoreRepository scores;
    @Inject ReputationSettings settings;
    @Inject SystemTx system;

    void onReview(@Observes @Outboxed ReviewSubmitted e) {
        recalculate(e.reviewedOrgId());
    }

    public void recalculate(UUID orgId) {
        system.run(() -> {
            scores.lockOrCreate(orgId);
            scores.save(TrustScoreCalculator.score(orgId, reviews.listReceived(orgId), settings.priorMean(),
                    settings.priorWeight(), OffsetDateTime.now()));
        });
    }
}
