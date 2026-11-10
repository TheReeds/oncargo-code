package pe.logyx.reputation.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import pe.logyx.reputation.application.port.out.ReputationScoreRepository;
import pe.logyx.reputation.domain.model.ReputationScore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheReputationScoreRepository implements ReputationScoreRepository {

    @Override
    public Optional<ReputationScore> find(UUID orgId) {
        return ReputationScoreEntity.<ReputationScoreEntity>findByIdOptional(orgId).map(ReputationScoreEntity::toDomain);
    }

    @Override
    public List<ReputationScore> findAll(Collection<UUID> orgIds) {
        if (orgIds.isEmpty()) return List.of();
        return ReputationScoreEntity.<ReputationScoreEntity>list("orgId in ?1", orgIds).stream()
                .map(ReputationScoreEntity::toDomain).toList();
    }

    @Override
    public void lockOrCreate(UUID orgId) {
        var em = ReputationScoreEntity.getEntityManager();
        em.createNativeQuery("INSERT INTO reputation_scores (org_id) VALUES (?1) ON CONFLICT (org_id) DO NOTHING")
                .setParameter(1, orgId).executeUpdate();
        ReputationScoreEntity.findById(orgId, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public void save(ReputationScore score) {
        ReputationScoreEntity e = ReputationScoreEntity.findById(score.orgId());
        if (e == null) {
            e = new ReputationScoreEntity();
            e.apply(score);
            e.persist();
        } else {
            e.apply(score);
        }
    }
}
