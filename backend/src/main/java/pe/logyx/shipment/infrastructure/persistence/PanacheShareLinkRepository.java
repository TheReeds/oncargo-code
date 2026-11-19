package pe.logyx.shipment.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.shipment.application.port.out.ShareLinkRepository;
import pe.logyx.shipment.domain.model.ShareLink;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheShareLinkRepository implements ShareLinkRepository {

    @Override
    public void insert(ShareLink link) {
        ShareLinkEntity.of(link).persist();
    }

    @Override
    public List<ShareLink> listByShipment(UUID shipmentId) {
        return ShareLinkEntity.<ShareLinkEntity>list("shipmentId = ?1 order by createdAt desc, id", shipmentId)
                .stream().map(ShareLinkEntity::toDomain).toList();
    }

    @Override
    public Optional<ShareLink> findById(UUID id) {
        // Lectura fresca (tras revocar con una sentencia propia, la entidad gestionada quedaría desactualizada).
        var em = ShareLinkEntity.getEntityManager();
        em.flush();
        ShareLinkEntity e = em.find(ShareLinkEntity.class, id);
        if (e != null) em.refresh(e);
        return Optional.ofNullable(e).map(ShareLinkEntity::toDomain);
    }

    @Override
    public boolean revoke(UUID id, UUID byProfileId, OffsetDateTime at) {
        int n = ShareLinkEntity.getEntityManager().createNativeQuery(
                        "UPDATE shipment_share_links SET revoked_at = ?1, revoked_by = ?2 WHERE id = ?3 AND revoked_at IS NULL")
                .setParameter(1, at).setParameter(2, byProfileId).setParameter(3, id).executeUpdate();
        return n > 0;
    }

    @Override
    public Optional<ShareLink> findByHash(String tokenHash) {
        return ShareLinkEntity.<ShareLinkEntity>find("tokenHash", tokenHash).firstResultOptional()
                .map(ShareLinkEntity::toDomain);
    }

    @Override
    public void recordView(UUID id, OffsetDateTime at) {
        ShareLinkEntity.getEntityManager().createNativeQuery(
                        "UPDATE shipment_share_links SET views = views + 1, last_viewed_at = ?1 WHERE id = ?2")
                .setParameter(1, at).setParameter(2, id).executeUpdate();
    }
}
