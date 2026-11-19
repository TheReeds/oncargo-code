package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.marketplace.application.port.out.BidBackupRepository;
import pe.logyx.marketplace.domain.model.BackupState;
import pe.logyx.marketplace.domain.model.BidBackup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheBidBackupRepository implements BidBackupRepository {

    @Override
    public Optional<BidBackup> findByBid(UUID bidId) {
        return BidBackupEntity.<BidBackupEntity>findByIdOptional(bidId).map(BidBackupEntity::toDomain);
    }

    @Override
    public List<BidBackup> listByRequest(UUID requestId) {
        return BidBackupEntity.<BidBackupEntity>list("requestId = ?1 order by rank", requestId).stream()
                .map(BidBackupEntity::toDomain).toList();
    }

    @Override
    public List<BidBackup> listRequestedForCarrier(UUID carrierOrgId) {
        return BidBackupEntity.<BidBackupEntity>list("carrierOrgId = ?1 and state <> ?2 order by requestedAt desc",
                carrierOrgId, BackupState.AVAILABLE).stream().map(BidBackupEntity::toDomain).toList();
    }

    @Override
    public Collection<BidBackup> listByRequests(Collection<UUID> requestIds) {
        if (requestIds.isEmpty()) return List.of();
        return BidBackupEntity.<BidBackupEntity>list("requestId in ?1", requestIds).stream()
                .map(BidBackupEntity::toDomain).toList();
    }

    @Override
    public void save(BidBackup backup) {
        if (BidBackupEntity.findById(backup.bidId()) == null) {
            BidBackupEntity e = new BidBackupEntity();
            e.apply(backup);
            e.persist();
        } else {
            // UPDATE por columna: logyx_app solo puede cambiar estado y fechas (V21), así que no se reescribe la fila.
            BidBackupEntity.update("state = ?1, requestedAt = ?2, respondedAt = ?3, usedAt = ?4 where bidId = ?5",
                    backup.state(), backup.requestedAt(), backup.respondedAt(), backup.usedAt(), backup.bidId());
        }
    }
}
