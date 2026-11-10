package pe.logyx.reputation.application.port.out;

import pe.logyx.reputation.domain.model.ReputationScore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReputationScoreRepository {
    Optional<ReputationScore> find(UUID orgId);
    List<ReputationScore> findAll(Collection<UUID> orgIds);
    /** Crea la fila si no existe y la bloquea (SELECT ... FOR UPDATE) para serializar recálculos. Solo SystemTx. */
    void lockOrCreate(UUID orgId);
    /** Inserta o actualiza. Solo SystemTx (logyx_app no tiene permisos de escritura). */
    void save(ReputationScore score);
}
